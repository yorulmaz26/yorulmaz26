"""football-data.co.uk erişilemediğinde GitHub yansısından okuma (ağ kullanılmaz)."""
from __future__ import annotations

import io
import urllib.error

import pytest

from akilli_tahmin.data import football_data_feed as F

CSV = b"\xef\xbb\xbfDiv,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG\r\nT1,22/11/2026,13:30,A,B,1,0\r\n"


class _Resp(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def _opener(calls, primary_error=None, mirror_body=CSV):
    def urlopen(req, timeout=None):
        url = req.full_url
        calls.append(url)
        if url.startswith(F.BASE_URL):
            if primary_error is None:
                return _Resp(CSV)
            raise primary_error
        return _Resp(mirror_body)
    return urlopen


def test_mirror_used_when_primary_blocked(monkeypatch, tmp_path):
    calls = []
    err = urllib.error.URLError("[SSL: CERTIFICATE_VERIFY_FAILED] self-signed certificate")
    monkeypatch.setattr(F.urllib.request, "urlopen", _opener(calls, err))
    monkeypatch.setattr(F.time, "sleep", lambda s: None)
    f = F.Fetcher(tmp_path, mirrors=("https://mirror.example/fd",))
    assert "HomeTeam" in f.get("mmz4281/2425/T1.csv", cache=True)
    assert f.primary_down and any(u.startswith("https://mirror.example/fd/") for u in calls)
    assert (tmp_path / "mmz4281_2425_T1.csv").exists()  # bitmiş sezon önbelleğe yazılır
    calls.clear()
    f.get("fixtures.csv", cache=False)
    assert calls[0].startswith("https://mirror.example/fd/")  # ikinci dosyada asıl kaynak beklenmez
    assert "[GitHub yansısı]" in f.log[-1]


def test_primary_preferred_and_invalid_mirror_rejected(monkeypatch):
    calls = []
    monkeypatch.setattr(F.urllib.request, "urlopen", _opener(calls))
    f = F.Fetcher(None, mirrors=("https://mirror.example/fd",))
    f.get("fixtures.csv", cache=False)
    assert len(calls) == 1 and calls[0].startswith(F.BASE_URL)
    calls.clear()
    monkeypatch.setattr(F.urllib.request, "urlopen",
                        _opener(calls, urllib.error.URLError("unreachable"), b"<html>404</html>"))
    monkeypatch.setattr(F.time, "sleep", lambda s: None)
    with pytest.raises(RuntimeError) as exc:
        F.Fetcher(None, mirrors=("https://mirror.example/fd",)).get("fixtures.csv", cache=False)
    assert F.BASE_URL in str(exc.value)  # ilk (asıl kaynak) hatası bildirilir


def test_mirror_env_override(monkeypatch):
    monkeypatch.setenv("AKILLI_TAHMIN_DATA_MIRROR", "0")
    assert F.default_mirrors() == ()
    monkeypatch.setenv("AKILLI_TAHMIN_DATA_MIRROR", "https://x.example/d/")
    assert F.default_mirrors() == ("https://x.example/d",)
    monkeypatch.delenv("AKILLI_TAHMIN_DATA_MIRROR")
    mirrors = F.default_mirrors()
    assert mirrors[0] == F.MIRROR_URL and all(m.startswith("file:") for m in mirrors[1:])


def test_local_copy_fallback(monkeypatch, tmp_path):
    """GitHub'a da erişilemezse projedeki yerel kopya (file://) kullanılır."""
    (tmp_path / "fixtures.csv").write_bytes(CSV)

    def urlopen(req, timeout=None):
        if req.full_url.startswith("file:"):
            return _Resp((tmp_path / "fixtures.csv").read_bytes())
        raise urllib.error.URLError("unreachable")

    monkeypatch.setattr(F.urllib.request, "urlopen", urlopen)
    monkeypatch.setattr(F.time, "sleep", lambda s: None)
    f = F.Fetcher(None, mirrors=("https://mirror.example/fd", tmp_path.as_uri()))
    assert "HomeTeam" in f.get("fixtures.csv", cache=False) and "[yerel kopya]" in f.log[-1]
