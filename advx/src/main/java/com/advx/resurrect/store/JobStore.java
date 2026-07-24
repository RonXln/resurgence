package com.advx.resurrect.store;

import com.advx.resurrect.model.JobState;
import com.advx.resurrect.model.ProgressEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 简单的内存任务存储 + SSE 订阅广播。
 * MVP 阶段够用；后期可换 Redis。
 */
@Component
public class JobStore {

    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<ProgressEvent>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<ProgressEvent>> history = new ConcurrentHashMap<>();

    public JobState create() {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JobState st = new JobState(id);
        jobs.put(id, st);
        subscribers.put(id, new CopyOnWriteArrayList<>());
        history.put(id, new CopyOnWriteArrayList<>());
        return st;
    }

    public JobState get(String jobId) {
        return jobs.get(jobId);
    }

    /** 订阅进度。返回一个反注册函数。 */
    public Runnable subscribe(String jobId, Consumer<ProgressEvent> listener) {
        List<Consumer<ProgressEvent>> list = subscribers.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        // 回放历史事件（客户端后连上也能看到之前的进度）
        List<ProgressEvent> past = history.getOrDefault(jobId, List.of());
        for (ProgressEvent e : past) {
            try { listener.accept(e); } catch (Exception ignored) {}
        }
        return () -> list.remove(listener);
    }

    public void publish(ProgressEvent event) {
        history.computeIfAbsent(event.jobId(), k -> new CopyOnWriteArrayList<>()).add(event);
        List<Consumer<ProgressEvent>> list = subscribers.get(event.jobId());
        if (list != null) {
            for (Consumer<ProgressEvent> l : list) {
                try { l.accept(event); } catch (Exception ignored) {}
            }
        }
    }
}
