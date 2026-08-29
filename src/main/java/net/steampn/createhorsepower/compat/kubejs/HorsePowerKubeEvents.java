package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface HorsePowerKubeEvents {
    EventGroup GROUP = EventGroup.of("HorsePowerEvents");

    EventHandler BEFORE_ATTACH = GROUP.server("beforeAttach", () -> BeforeAttachKubeEvent.class).hasResult();
    EventHandler WORKER_ATTACHED = GROUP.server("workerAttached", () -> WorkerAttachedKubeEvent.class);
    EventHandler WORKER_DETACHED = GROUP.server("workerDetached", () -> WorkerDetachedKubeEvent.class);
    EventHandler WORK_STARTED = GROUP.server("workStarted", () -> WorkStartedKubeEvent.class).hasResult();
    EventHandler WORK_STOPPED = GROUP.server("workStopped", () -> WorkStoppedKubeEvent.class);
    EventHandler OUTPUT_CALCULATED = GROUP.server("outputCalculated", () -> OutputCalculatedKubeEvent.class);
    EventHandler PATH_EVALUATED = GROUP.server("pathEvaluated", () -> PathEvaluatedKubeEvent.class);
}
