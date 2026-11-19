# Unity katmanı sözleşmeleri (paralel geliştirme)

Bütün Unity kodu `Assets/_Project/Scripts/Game/` altında, `namespace WorldWonders.Game`. Oyun kuralları **yalnız** `WorldWonders.Core` servislerinde (`GameManager.I.Session`) uygulanır; görünüm katmanı kural tekrarlamaz, servis çağırır.

## Derleme denetimi
`cd WorldWonders/Tools/UnityCompileCheck && dotnet build` — UnityEngine.Modules 2021.3 + Input System taslağı ile derler.
Unity 6'ya özgü olup 2021.3'te olmayan API kullanmayın (ör. `Painter2D`, `Awaitable`, `Rigidbody.linearVelocity`, `com.unity.ai.navigation` NavMeshSurface). uGUI/TextMeshPro kullanmayın: UI = **UI Toolkit, kodla**. Eski `UnityEngine.Input` kullanmayın: girdi = `InputAdapter`.
C# 9; `record`, `init`, dosya kapsamlı namespace yok. Unity nesnelerinde `??` / `?.` kullanmayın.

## Altyapı (hazır)
- `GameManager.I`: `Session` (GameSession), `Levels` (LevelRepository.Get(global)), `UI` (UIFlowController), `Cam`, `Rig` (CameraRig), `Sun`, `ShowView<T>()`, `GetView<T>()`, `SaveNow()`, `ReplaceSave(SaveData)`, `Saves` (Export/List/Import backup).
- `ViewBase : MonoBehaviour` — `protected abstract void Build()` (bir kez), `virtual Enter()/Exit()`. Görünümler kendi geometrisini `transform` altında kurar. Aynı anda bir görünüm etkin.
- `UIScreen` — `protected abstract void Build(VisualElement root)`, `OnShow()` (her görünür oluşta; 3D görünümü burada `Game.ShowView<...>()` ile etkinleştirin), `OnHide()`, `Tick(dt)`, `OnBack()`, `ShowHud`.
- `UIFlowController`: `Push/Replace/ResetTo/Back/PopTo<T>/ClearStack`, `ShowPopup(content, dismissOnBackdrop, onDismiss) → VisualElement`, `ClosePopup(p)`, `CloseAllPopups()`, `Confirm(...)`, `Info(...)`, `Toast(text, color)`, `RefreshHud()`, `IsOverUI(screenPos)`.
- `UIKit`: `Page(root, title, onBack, out ScrollView content, opaque, hudSpace)`, `Card()`, `Row()`, `Column()`, `Spacer(h)`, `Flex()`, `Scroll()`, `Text/Title/H2/Body/Small`, `Pill`, `Primary/Gold/Secondary/BaseButton`, `IconButton(Icon, onClick, bg, fg, size, badge)`, `MenuTile(Icon, label, onClick, tint, locked)`, `IconImage(Icon, tint, size)`, `Stars(n)`, `Chip(...)`, `SetEnabled(btn, bool)`, uzantılar `.Pad .Margin .Radius .Border .Bg .Size .Grow .Absolute .Fill .Center .Ignore`, `ProgressBar(color, h, label).Set(t, label)`. Sabitler `HudHeight=170`, `Gutter=40`, `FontTitle/H2/Body/Small/Tiny`.
- `Icon` enum + `IconFactory.Get(Icon)` (prosedürel beyaz ikonlar; tint ile renklenir).
- `Palette` (Cream, Turquoise, Gold, Green, Text, TextSoft, Coral, Lilac, Sky, Sand, Stone, Marble, Terracotta, Grass, Water, Locked, Fog, `TileColors[6]`, `Hex()`, `Lighten/Darken/WithAlpha`).
- `MaterialLibrary.Get(Color, emission)` (alfa<1 → saydam şader). `Prim.Group/Create/Box/Cyl/Ball/Capsule/Cone/FromMesh/SetColor/ClearChildren`, `MeshFactory.Cone/Ring/Dome/Prism`. Kök nesneye `PlaceholderAsset.Mark(go, note)` ekleyin (geçici varlık işareti).
- `Tween.Run(duration, step(k), done, ease, delay)`, `Tween.Move/Scale/Delay`, `Ease`.
- `InputAdapter` (Position, Delta, Down, Held, Up, Tap, Dragging, StartedOverUI, Pinch, TouchCount, WorldGesture).
- `CameraRig.Configure(bounds, distance, min, max, pitch, yaw, pivot)`, `SetMode(CameraMode.Follow/FreePan/Focus)`, `FollowTarget`, `FocusOn(point, dist, dur)`, `AllowPan/AllowZoom`.
- `Tappable` (OnTap, ApproachPoint, WalkToFirst, OnArrive), `WalkableGround`, `WorldPicker.Pick(cam, screenPos, out tappable, out groundPoint, out hitGround)`. Dünya dokunuşunu yalnız `InputAdapter.Tap` (UI'da başlamamış) ile işleyin → "UI dokunuşları yürüyüş başlatmaz".
- `Haptics.Strong()`, `Nav.*` (tüm geçişler).

## Sınıf sahipliği ve ortak API'ler
| Sahip | Dosyalar / sınıflar | Diğerlerinin kullandığı API |
|---|---|---|
| A | `Match3/Match3BoardView.cs`, `Match3/Match3Screen.cs` (+ sonuç ekranı), `Match3/LevelPreviewPopup.cs` | `LevelPreviewPopup.Show(int global)`, `new Match3Screen(int global)` |
| B | `Views/MonumentBuilder.cs`, `Views/WorldMapView.cs`, `Views/ConstructionView.cs`, `Screens/WorldMapScreen.cs` | `MonumentModel MonumentBuilder.Build(int wonder, Transform parent)`; `MonumentModel.SetBuilt(int group, bool built, bool animate)`, `SyncFrom(GameSession)`, `Wonder`, `Height`; `ConstructionView : ViewBase` → `Show(int wonder)`, `AnimatePlace(int group)`; `new WorldMapScreen()` |
| C1 | `Explorer/ExplorerCharacter.cs`, `Explorer/ExplorerMovementController.cs`, `Explorer/RuntimeNavMesh.cs`, `Explorer/CharacterPreview.cs`, `Views/DestinationMapView.cs`, `Screens/DestinationScreen.cs` | `enum ExplorerAnim { Idle, Walk, Turn, Interact, Discover, Celebrate }`; `ExplorerCharacter.Create(Transform parent)`, `.ApplyLoadout(IDictionary<CosmeticSlot,string>)`, `.Play(ExplorerAnim)`; `ExplorerMovementController.Attach(ExplorerCharacter)`, `.WalkTo(Vector3, Action onArrive)`, `.Warp(Vector3)`, `.IsMoving`; `RuntimeNavMesh.Build(Transform root, Bounds b)` / `Clear()`; `CharacterPreview.Get()` → `.Texture` (RenderTexture), `.Refresh()`, `.SetYaw(float)`, `.Play(ExplorerAnim)`, `.SetVisible(bool)`; `new DestinationScreen(int wonder)` (`public readonly int Wonder`) |
| C2 | `Museum/MuseumView.cs`, `Museum/ArtifactModelFactory.cs`, `Museum/MuseumScreen.cs`, `Museum/ArtifactInspectScreen.cs`, `Museum/PersonalRoomScreen.cs` | `GameObject ArtifactModelFactory.Build(ArtifactInfo a, Transform parent, bool collected)` (~1 birim boy); `new MuseumScreen()`, `new PersonalRoomScreen()`, `new ArtifactInspectScreen(ArtifactInfo)` |
| D | `Screens/JigsawScreen.cs`, `Minigames/MinigameScreen.cs`, `Screens/DailyMissionsScreen.cs`, `Screens/ProfileScreen.cs`, `Screens/CustomizationScreen.cs`, `Screens/StoreScreen.cs`, `Screens/ToolsScreen.cs`, `Screens/SettingsScreen.cs` | `new JigsawScreen(int wonder, int highlightPiece)`, `MinigameScreen.ForDiscovery(w, area)`, `MinigameScreen.ForDaily(ToolType)` |

Başka bir sahibin sınıfına ihtiyaç duyulursa yalnız yukarıdaki API'yi kullanın; eksikse kendi dosyanızda geçici bir kopya yazmayın, raporda belirtin. Mevcut iskelet dosyaları sahibi tarafından tamamen değiştirilir.
