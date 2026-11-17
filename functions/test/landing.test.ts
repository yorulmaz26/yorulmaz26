import { escapeHtml, renderLanding } from "../src/salon/landing";
import { isInTurkey } from "../src/salon/updateSalon";

test("HTML kaçışı", () => {
  expect(escapeHtml(`<script>"x"&'y'</script>`)).toBe("&lt;script&gt;&quot;x&quot;&amp;&#39;y&#39;&lt;/script&gt;");
});

test("web sayfası yalnızca kamuya açık alanları ve uygulama bağlantısını içerir", () => {
  const html = renderLanding({
    code: "482913", name: "Işıltı <Kuaför>", type: "UNISEX_HAIRDRESSER", city: "İstanbul", district: "Kadıköy",
    logoUrl: "https://x/logo.jpg", ratingAvg: 4.66,
  });
  expect(html).toContain("Işıltı &lt;Kuaför&gt;");
  expect(html).not.toContain("<Kuaför>");
  expect(html).toContain("Erkek &amp; Kadın Kuaförü");
  expect(html).toContain("Kadıköy / İstanbul");
  expect(html).toContain("★ 4,7");
  expect(html).toContain("intent://salon/482913#Intent;scheme=kuaforumcepte;package=com.kuaforumcepte");
  expect(renderLanding({ code: "1", name: "a", type: "BEAUTY_SALON", city: "", district: "", logoUrl: "", ratingAvg: null })).toContain("Yeni");
});

test("koordinat Türkiye sınırında", () => {
  expect(isInTurkey(40.99, 29.02)).toBe(true);
  expect(isInTurkey(48.85, 2.35)).toBe(false);
});
