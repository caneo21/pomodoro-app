using System.Numerics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Hosting;

namespace PomodoroWindows.Controls;

/// <summary>
/// Wires up a small spring-based scale-down-on-press, spring-back-on-release effect on any
/// UIElement, matching the shared design spec's button press motion token.
/// </summary>
public static class PressScaleBehavior
{
    private const float PressedScale = 0.94f;

    public static void Attach(UIElement element)
    {
        var visual = ElementCompositionPreview.GetElementVisual(element);
        var compositor = visual.Compositor;

        void UpdateCenterPoint()
        {
            if (element is FrameworkElement frameworkElement)
            {
                visual.CenterPoint = new Vector3(
                    (float)(frameworkElement.ActualWidth / 2.0),
                    (float)(frameworkElement.ActualHeight / 2.0),
                    0f);
            }
        }

        UpdateCenterPoint();
        if (element is FrameworkElement trackedElement)
        {
            trackedElement.SizeChanged += (_, _) => UpdateCenterPoint();
        }

        var spring = compositor.CreateSpringVector3Animation();
        spring.Target = "Scale";
        spring.DampingRatio = 0.5f;
        spring.Period = TimeSpan.FromMilliseconds(150);

        void AnimateTo(float scale)
        {
            spring.FinalValue = new Vector3(scale, scale, 1f);
            visual.StartAnimation("Scale", spring);
        }

        element.PointerPressed += (_, _) => AnimateTo(PressedScale);
        element.PointerReleased += (_, _) => AnimateTo(1f);
        element.PointerCanceled += (_, _) => AnimateTo(1f);
        element.PointerCaptureLost += (_, _) => AnimateTo(1f);
    }
}
