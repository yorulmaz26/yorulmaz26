namespace WorldWonders.Core
{
    public enum Difficulty { Easy = 0, Medium = 1, Hard = 2, VeryHard = 3 }

    /// <summary>Altı temel taş. None = boş hücre.</summary>
    public enum TileColor { None = -1, GoldCoin = 0, Scroll = 1, Amphora = 2, Scarab = 3, Compass = 4, Crown = 5 }

    public enum SpecialType { None = 0, ArrowRow = 1, ArrowColumn = 2, Bomb = 3, TimeOrb = 4 }

    public enum ToolType { Magnifier = 0, Brush = 1, Trowel = 2 }

    public enum BoosterType { AncientArrow = 0, AncientBomb = 1, Hammer = 2, Shuffle = 3 }

    public enum DiscoveryKind { Artifact = 0, Chest = 1 }

    public enum CosmeticSlot { Body = 0, Hair = 1, SkinTone = 2, Outfit = 3, Shoes = 4, Hat = 5, Bag = 6, Accessory = 7, OutfitColor = 8, BagColor = 9, ProfileFrame = 10, MuseumDecoration = 11 }
}
