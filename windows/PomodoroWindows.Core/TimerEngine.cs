namespace PomodoroWindows.Core;

public sealed class TimerEngine
{
    private static readonly long MinPhaseMillis = Math.Min(
        TimerDefaults.MinFocusMinutes,
        Math.Min(TimerDefaults.MinBreakMinutes, TimerDefaults.MinLongBreakMinutes)) * 60_000L;

    private long _focusMillis;
    private long _shortBreakMillis;
    private long _longBreakMillis;
    private int _pomodorosUntilLongBreak;
    private bool _autoStartBreak;
    private bool _autoStartFocus;

    private TimerPhase _phase = TimerPhase.Focus;
    private bool _isRunning;
    private long _endTimestamp;
    private long _remainingWhenIdle;
    private int _completedPomodoros;
    private long _currentPhaseDurationMillis;

    public TimerEngine(
        long focusMillis,
        long shortBreakMillis,
        long longBreakMillis = TimerDefaults.DefaultLongBreakMinutes * 60_000L,
        int pomodorosUntilLongBreak = TimerDefaults.DefaultPomodorosUntilLongBreak,
        bool autoStartBreak = false,
        bool autoStartFocus = false)
    {
        _focusMillis = focusMillis;
        _shortBreakMillis = shortBreakMillis;
        _longBreakMillis = longBreakMillis;
        _pomodorosUntilLongBreak = pomodorosUntilLongBreak;
        _autoStartBreak = autoStartBreak;
        _autoStartFocus = autoStartFocus;
        _remainingWhenIdle = focusMillis;
        _currentPhaseDurationMillis = focusMillis;
    }

    private long DurationFor(TimerPhase phase) => phase switch
    {
        TimerPhase.Focus => _focusMillis,
        TimerPhase.ShortBreak => _shortBreakMillis,
        TimerPhase.LongBreak => _longBreakMillis,
        _ => throw new ArgumentOutOfRangeException(nameof(phase))
    };

    private TimerPhase NextPhase()
    {
        switch (_phase)
        {
            case TimerPhase.Focus:
                _completedPomodoros += 1;
                return _completedPomodoros >= _pomodorosUntilLongBreak ? TimerPhase.LongBreak : TimerPhase.ShortBreak;
            case TimerPhase.ShortBreak:
                return TimerPhase.Focus;
            case TimerPhase.LongBreak:
                _completedPomodoros = 0;
                return TimerPhase.Focus;
            default:
                throw new ArgumentOutOfRangeException();
        }
    }

    private bool AutoStartAllowed(TimerPhase departingPhase) =>
        departingPhase == TimerPhase.Focus ? _autoStartBreak : _autoStartFocus;

    private void AdvanceAcrossPhaseBoundaries(long now)
    {
        while (_isRunning && now >= _endTimestamp)
        {
            var departingPhase = _phase;
            _phase = NextPhase();
            _currentPhaseDurationMillis = DurationFor(_phase);
            if (AutoStartAllowed(departingPhase))
            {
                _endTimestamp += _currentPhaseDurationMillis;
            }
            else
            {
                _isRunning = false;
                _remainingWhenIdle = _currentPhaseDurationMillis;
            }
        }
    }

    public TimerState CurrentState(long now)
    {
        var remaining = _isRunning ? Math.Max(_endTimestamp - now, 0) : _remainingWhenIdle;
        return new TimerState(_phase, _isRunning, remaining, _currentPhaseDurationMillis, _completedPomodoros);
    }

    public TimerState Start(long now)
    {
        if (!_isRunning)
        {
            _isRunning = true;
            _endTimestamp = now + _remainingWhenIdle;
        }
        return CurrentState(now);
    }

    public TimerState Pause(long now)
    {
        if (_isRunning)
        {
            _remainingWhenIdle = Math.Max(_endTimestamp - now, 0);
            _isRunning = false;
        }
        return CurrentState(now);
    }

    public TimerState Reset(long now)
    {
        _isRunning = false;
        _phase = TimerPhase.Focus;
        _remainingWhenIdle = _focusMillis;
        _currentPhaseDurationMillis = _focusMillis;
        return CurrentState(now);
    }

    public TimerState Tick(long now)
    {
        if (!_isRunning) return CurrentState(now);

        AdvanceAcrossPhaseBoundaries(now);
        if (_isRunning)
        {
            _remainingWhenIdle = Math.Max(_endTimestamp - now, 0);
        }
        return CurrentState(now);
    }

    public void UpdateDurations(
        long focusMillis,
        long shortBreakMillis,
        long? longBreakMillis = null,
        int? pomodorosUntilLongBreak = null,
        bool? autoStartBreak = null,
        bool? autoStartFocus = null)
    {
        _focusMillis = Math.Max(focusMillis, MinPhaseMillis);
        _shortBreakMillis = Math.Max(shortBreakMillis, MinPhaseMillis);
        _longBreakMillis = Math.Max(longBreakMillis ?? _longBreakMillis, MinPhaseMillis);
        _pomodorosUntilLongBreak = Math.Max(pomodorosUntilLongBreak ?? _pomodorosUntilLongBreak, 1);
        _autoStartBreak = autoStartBreak ?? _autoStartBreak;
        _autoStartFocus = autoStartFocus ?? _autoStartFocus;
        if (!_isRunning)
        {
            _remainingWhenIdle = DurationFor(_phase);
            _currentPhaseDurationMillis = DurationFor(_phase);
        }
    }
}
