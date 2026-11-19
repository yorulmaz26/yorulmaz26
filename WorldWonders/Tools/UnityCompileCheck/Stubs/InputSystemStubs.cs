// YALNIZ DERLEME DENETİMİ İÇİN: com.unity.inputsystem paketinin bu projede kullanılan alt kümesinin imzaları.
// Unity projesine dahil DEĞİLDİR (Assets dışında).
using UnityEngine;
namespace UnityEngine.InputSystem.Controls
{
    public class ButtonControl { public bool isPressed => false; public bool wasPressedThisFrame => false; public bool wasReleasedThisFrame => false; }
    public class Vector2Control { public Vector2 ReadValue() => default; }
}
namespace UnityEngine.InputSystem
{
    using UnityEngine.InputSystem.Controls;
    public class Pointer { public Vector2Control position => null; public ButtonControl press => null; public static Pointer current => null; }
    public class Mouse : Pointer { public new static Mouse current => null; public ButtonControl leftButton => null; public Vector2Control scroll => null; }
    public class Keyboard { public static Keyboard current => null; public ButtonControl escapeKey => null; }
    public enum TouchPhase { None, Began, Moved, Ended, Canceled, Stationary }
}
namespace UnityEngine.InputSystem.Utilities
{
    public struct ReadOnlyArray<T> { public int Count => 0; public T this[int i] => default; }
}
namespace UnityEngine.InputSystem.EnhancedTouch
{
    public static class EnhancedTouchSupport { public static void Enable() { } }
    public struct Touch
    {
        public static UnityEngine.InputSystem.Utilities.ReadOnlyArray<Touch> activeTouches => default;
        public Vector2 screenPosition => default; public Vector2 delta => default; public UnityEngine.InputSystem.TouchPhase phase => default; public int touchId => 0;
    }
}
