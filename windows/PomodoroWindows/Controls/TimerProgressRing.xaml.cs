using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;

namespace PomodoroWindows.Controls;

public sealed partial class TimerProgressRing : UserControl
{
    public static readonly DependencyProperty DiameterProperty = DependencyProperty.Register(
        nameof(Diameter), typeof(double), typeof(TimerProgressRing),
        new PropertyMetadata(240.0, OnLayoutPropertyChanged));

    public static readonly DependencyProperty RingThicknessProperty = DependencyProperty.Register(
        nameof(RingThickness), typeof(double), typeof(TimerProgressRing),
        new PropertyMetadata(8.0, OnLayoutPropertyChanged));

    public static readonly DependencyProperty ProgressProperty = DependencyProperty.Register(
        nameof(Progress), typeof(double), typeof(TimerProgressRing),
        new PropertyMetadata(0.0, OnLayoutPropertyChanged));

    public static readonly DependencyProperty RingBrushProperty = DependencyProperty.Register(
        nameof(RingBrush), typeof(Brush), typeof(TimerProgressRing),
        new PropertyMetadata(null, OnBrushPropertyChanged));

    public static readonly DependencyProperty TrackBrushProperty = DependencyProperty.Register(
        nameof(TrackBrush), typeof(Brush), typeof(TimerProgressRing),
        new PropertyMetadata(null, OnBrushPropertyChanged));

    public double Diameter
    {
        get => (double)GetValue(DiameterProperty);
        set => SetValue(DiameterProperty, value);
    }

    public double RingThickness
    {
        get => (double)GetValue(RingThicknessProperty);
        set => SetValue(RingThicknessProperty, value);
    }

    public double Progress
    {
        get => (double)GetValue(ProgressProperty);
        set => SetValue(ProgressProperty, value);
    }

    public Brush? RingBrush
    {
        get => (Brush?)GetValue(RingBrushProperty);
        set => SetValue(RingBrushProperty, value);
    }

    public Brush? TrackBrush
    {
        get => (Brush?)GetValue(TrackBrushProperty);
        set => SetValue(TrackBrushProperty, value);
    }

    public TimerProgressRing()
    {
        InitializeComponent();
        UpdateBrushes();
        UpdateGeometry();
    }

    private static void OnLayoutPropertyChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
        ((TimerProgressRing)d).UpdateGeometry();

    private static void OnBrushPropertyChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
        ((TimerProgressRing)d).UpdateBrushes();

    private void UpdateBrushes()
    {
        TrackEllipse.Stroke = TrackBrush;
        ProgressPath.Stroke = RingBrush;
    }

    private void UpdateGeometry()
    {
        RootGrid.Width = Diameter;
        RootGrid.Height = Diameter;

        var inset = Math.Max(Diameter - RingThickness, 0);
        TrackEllipse.Width = inset;
        TrackEllipse.Height = inset;
        TrackEllipse.StrokeThickness = RingThickness;

        ProgressPath.StrokeThickness = RingThickness;
        ProgressPath.Data = BuildArcGeometry(Diameter, RingThickness, Progress);
    }

    private static PathGeometry? BuildArcGeometry(double diameter, double thickness, double progress)
    {
        if (progress <= 0.0005) return null;

        var radius = (diameter - thickness) / 2.0;
        var center = new Point(diameter / 2.0, diameter / 2.0);
        // A single ArcSegment degenerates when its start and end point coincide, which is
        // exactly what a mathematically-complete 360-degree sweep would do — clamping just
        // under a full turn keeps the arc well-defined with a visually imperceptible gap.
        var sweepAngle = Math.Min(progress, 0.9995) * 360.0;

        var startPoint = PointOnCircle(center, radius, -90.0);
        var endPoint = PointOnCircle(center, radius, -90.0 + sweepAngle);

        var arcSegment = new ArcSegment
        {
            Point = endPoint,
            Size = new Size(radius, radius),
            SweepDirection = SweepDirection.Clockwise,
            IsLargeArc = sweepAngle > 180.0
        };

        var figure = new PathFigure { StartPoint = startPoint, IsClosed = false };
        figure.Segments.Add(arcSegment);

        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);
        return geometry;
    }

    private static Point PointOnCircle(Point center, double radius, double angleDegrees)
    {
        var radians = angleDegrees * Math.PI / 180.0;
        return new Point(
            center.X + radius * Math.Cos(radians),
            center.Y + radius * Math.Sin(radians));
    }
}
