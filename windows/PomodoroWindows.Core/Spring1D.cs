namespace PomodoroWindows.Core;

/// <summary>
/// A 1-dimensional damped-harmonic-oscillator spring, stepped manually once per rendered
/// frame. Used to give ring progress and ring color transitions a warm, physical settle
/// instead of snapping instantly to a new target.
/// </summary>
public sealed class Spring1D
{
    private readonly double _stiffness;
    private readonly double _damping;
    private double _velocity;

    public double Value { get; private set; }
    public double Target { get; private set; }

    public Spring1D(double initialValue, double stiffness, double damping)
    {
        Value = initialValue;
        Target = initialValue;
        _stiffness = stiffness;
        _damping = damping;
    }

    public void SetTarget(double target) => Target = target;

    public void Reset(double value)
    {
        Value = value;
        Target = value;
        _velocity = 0.0;
    }

    public bool IsSettled(double tolerance = 0.001) =>
        Math.Abs(Target - Value) < tolerance && Math.Abs(_velocity) < tolerance;

    public double Step(double deltaSeconds)
    {
        var displacement = Target - Value;
        var acceleration = _stiffness * displacement - _damping * _velocity;
        _velocity += acceleration * deltaSeconds;
        Value += _velocity * deltaSeconds;
        return Value;
    }
}
