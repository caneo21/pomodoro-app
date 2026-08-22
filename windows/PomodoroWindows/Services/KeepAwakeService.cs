using System.Runtime.InteropServices;

namespace PomodoroWindows.Services;

public sealed class KeepAwakeService
{
    [Flags]
    private enum ExecutionState : uint
    {
        Continuous = 0x80000000,
        SystemRequired = 0x00000001,
        DisplayRequired = 0x00000002
    }

    [DllImport("kernel32.dll")]
    private static extern ExecutionState SetThreadExecutionState(ExecutionState state);

    public void SetActive(bool active)
    {
        SetThreadExecutionState(active
            ? ExecutionState.Continuous | ExecutionState.SystemRequired | ExecutionState.DisplayRequired
            : ExecutionState.Continuous);
    }
}
