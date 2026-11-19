using UnityEngine.UIElements;

namespace WorldWonders.Game
{
    // İSKELET: paralel geliştirmede derlemeyi açık tutmak için; gerçek uygulamayla değiştirilecek.
    public sealed class DestinationScreen : UIScreen
    {
        public readonly int Wonder; public DestinationScreen(int wonder) { Wonder = wonder; }
        protected override void Build(VisualElement root) { }
    }
}
