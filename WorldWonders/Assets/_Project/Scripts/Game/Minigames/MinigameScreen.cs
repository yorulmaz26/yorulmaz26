using UnityEngine.UIElements;
using WorldWonders.Core;

namespace WorldWonders.Game
{
    // İSKELET
    public sealed class MinigameScreen : UIScreen
    {
        public static MinigameScreen ForDiscovery(int wonder, int area) { return new MinigameScreen(); }
        public static MinigameScreen ForDaily(ToolType tool) { return new MinigameScreen(); }
        protected override void Build(VisualElement root) { }
    }
}
