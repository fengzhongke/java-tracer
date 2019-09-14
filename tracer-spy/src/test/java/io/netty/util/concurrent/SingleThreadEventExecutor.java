//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package io.netty.util.concurrent;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public abstract class SingleThreadEventExecutor extends AbstractScheduledEventExecutor implements OrderedEventExecutor {
    static final int DEFAULT_MAX_PENDING_EXECUTOR_TASKS = Math.max(16, SystemPropertyUtil.getInt("io.netty.eventexecutor.maxPendingTasks", 2147483647));
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SingleThreadEventExecutor.class);
    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_SHUTTING_DOWN = 3;
    private static final int ST_SHUTDOWN = 4;
    private static final int ST_TERMINATED = 5;
    private static final Runnable WAKEUP_TASK = new Runnable() {
        public void run() {
        }
    };
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER;
    private final EventExecutorGroup parent;
    private final Queue<Runnable> taskQueue;
    private final Thread thread;
    private final ThreadProperties threadProperties;
    private final Semaphore threadLock;
    private final Set<Runnable> shutdownHooks;
    private final boolean addTaskWakesUp;
    private final int maxPendingTasks;
    private final RejectedExecutionHandler rejectedExecutionHandler;
    private long lastExecutionTime;
    private volatile int state;
    private volatile long gracefulShutdownQuietPeriod;
    private volatile long gracefulShutdownTimeout;
    private long gracefulShutdownStartTime;
    private final Promise<?> terminationFuture;
    private static final long SCHEDULE_PURGE_INTERVAL;

    protected SingleThreadEventExecutor(EventExecutorGroup parent, ThreadFactory threadFactory, boolean addTaskWakesUp) {
        this(parent, threadFactory, addTaskWakesUp, DEFAULT_MAX_PENDING_EXECUTOR_TASKS, RejectedExecutionHandlers.reject());
    }

    protected SingleThreadEventExecutor(EventExecutorGroup parent, ThreadFactory threadFactory, boolean addTaskWakesUp, int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this.threadLock = new Semaphore(0);
        this.shutdownHooks = new LinkedHashSet();
        this.state = 1;
        this.terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        } else {
            this.parent = parent;
            this.addTaskWakesUp = addTaskWakesUp;
            this.thread = threadFactory.newThread(new Runnable() {
                public void run() {
                    boolean success = false;
                    SingleThreadEventExecutor.this.updateLastExecutionTime();
                    boolean var112 = false;

                    int oldState;
                    label1642: {
                        try {
                            var112 = true;
                            SingleThreadEventExecutor.this.run();
                            success = true;
                            var112 = false;
                            break label1642;
                        } catch (Throwable var119) {
                            SingleThreadEventExecutor.logger.warn("Unexpected exception from an event executor: ", var119);
                            var112 = false;
                        } finally {
                            if (var112) {
                                int oldStatex;
                                do {
                                    oldStatex = SingleThreadEventExecutor.STATE_UPDATER.get(SingleThreadEventExecutor.this);
                                } while(oldStatex < 3 && !SingleThreadEventExecutor.STATE_UPDATER.compareAndSet(SingleThreadEventExecutor.this, oldStatex, 3));

                                if (success && SingleThreadEventExecutor.this.gracefulShutdownStartTime == 0L) {
                                    SingleThreadEventExecutor.logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " + SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must be called " + "before run() implementation terminates.");
                                }

                                try {
                                    while(!SingleThreadEventExecutor.this.confirmShutdown()) {
                                    }
                                } finally {
                                    try {
                                        SingleThreadEventExecutor.this.cleanup();
                                    } finally {
                                        SingleThreadEventExecutor.STATE_UPDATER.set(SingleThreadEventExecutor.this, 5);
                                        SingleThreadEventExecutor.this.threadLock.release();
                                        if (!SingleThreadEventExecutor.this.taskQueue.isEmpty()) {
                                            SingleThreadEventExecutor.logger.warn("An event executor terminated with non-empty task queue (" + SingleThreadEventExecutor.this.taskQueue.size() + ')');
                                        }

                                        SingleThreadEventExecutor.this.terminationFuture.setSuccess(null);
                                    }
                                }

                            }
                        }

                        do {
                            oldState = SingleThreadEventExecutor.STATE_UPDATER.get(SingleThreadEventExecutor.this);
                        } while(oldState < 3 && !SingleThreadEventExecutor.STATE_UPDATER.compareAndSet(SingleThreadEventExecutor.this, oldState, 3));

                        if (success && SingleThreadEventExecutor.this.gracefulShutdownStartTime == 0L) {
                            SingleThreadEventExecutor.logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " + SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must be called " + "before run() implementation terminates.");
                        }

                        try {
                            while(!SingleThreadEventExecutor.this.confirmShutdown()) {
                            }

                            return;
                        } finally {
                            try {
                                SingleThreadEventExecutor.this.cleanup();
                            } finally {
                                SingleThreadEventExecutor.STATE_UPDATER.set(SingleThreadEventExecutor.this, 5);
                                SingleThreadEventExecutor.this.threadLock.release();
                                if (!SingleThreadEventExecutor.this.taskQueue.isEmpty()) {
                                    SingleThreadEventExecutor.logger.warn("An event executor terminated with non-empty task queue (" + SingleThreadEventExecutor.this.taskQueue.size() + ')');
                                }

                                SingleThreadEventExecutor.this.terminationFuture.setSuccess(null);
                            }
                        }
                    }

                    do {
                        oldState = SingleThreadEventExecutor.STATE_UPDATER.get(SingleThreadEventExecutor.this);
                    } while(oldState < 3 && !SingleThreadEventExecutor.STATE_UPDATER.compareAndSet(SingleThreadEventExecutor.this, oldState, 3));

                    if (success && SingleThreadEventExecutor.this.gracefulShutdownStartTime == 0L) {
                        SingleThreadEventExecutor.logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " + SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must be called " + "before run() implementation terminates.");
                    }

                    try {
                        while(!SingleThreadEventExecutor.this.confirmShutdown()) {
                        }
                    } finally {
                        try {
                            SingleThreadEventExecutor.this.cleanup();
                        } finally {
                            SingleThreadEventExecutor.STATE_UPDATER.set(SingleThreadEventExecutor.this, 5);
                            SingleThreadEventExecutor.this.threadLock.release();
                            if (!SingleThreadEventExecutor.this.taskQueue.isEmpty()) {
                                SingleThreadEventExecutor.logger.warn("An event executor terminated with non-empty task queue (" + SingleThreadEventExecutor.this.taskQueue.size() + ')');
                            }

                            SingleThreadEventExecutor.this.terminationFuture.setSuccess(null);
                        }
                    }

                }
            });
            this.threadProperties = new SingleThreadEventExecutor.DefaultThreadProperties(this.thread);
            this.maxPendingTasks = Math.max(16, maxPendingTasks);
            this.taskQueue = this.newTaskQueue();
            this.rejectedExecutionHandler = (RejectedExecutionHandler)ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler");
        }
    }

    /** @deprecated */
    @Deprecated
    protected Queue<Runnable> newTaskQueue() {
        return this.newTaskQueue(this.maxPendingTasks);
    }

    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue(maxPendingTasks);
    }

    public EventExecutorGroup parent() {
        return this.parent;
    }

    protected void interruptThread() {
        this.thread.interrupt();
    }

    protected Runnable pollTask() {
        assert this.inEventLoop();

        Runnable task;
        do {
            task = (Runnable)this.taskQueue.poll();
        } while(task == WAKEUP_TASK);

        return task;
    }

    protected Runnable takeTask() {
        assert this.inEventLoop();

        if (!(this.taskQueue instanceof BlockingQueue)) {
            throw new UnsupportedOperationException();
        } else {
            BlockingQueue taskQueue = (BlockingQueue)this.taskQueue;

            Runnable task;
            do {
                ScheduledFutureTask<?> scheduledTask = this.peekScheduledTask();
                if (scheduledTask == null) {
                     task = null;

                    try {
                        task = (Runnable)taskQueue.take();
                        if (task == WAKEUP_TASK) {
                            task = null;
                        }
                    } catch (InterruptedException var9) {
                    }

                    return task;
                }

                long delayNanos = scheduledTask.delayNanos();
                task = null;
                if (delayNanos > 0L) {
                    try {
                        task = (Runnable)taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException var10) {
                        return null;
                    }
                }

                if (task == null) {
                    this.fetchFromScheduledTaskQueue();
                    task = (Runnable)taskQueue.poll();
                }
            } while(task == null);

            return task;
        }
    }

    private boolean fetchFromScheduledTaskQueue() {
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();

        for(Runnable scheduledTask = this.pollScheduledTask(nanoTime); scheduledTask != null; scheduledTask = this.pollScheduledTask(nanoTime)) {
            if (!this.taskQueue.offer(scheduledTask)) {
                this.scheduledTaskQueue().add((ScheduledFutureTask)scheduledTask);
                return false;
            }
        }

        return true;
    }

    protected Runnable peekTask() {
        assert this.inEventLoop();

        return (Runnable)this.taskQueue.peek();
    }

    protected boolean hasTasks() {
        assert this.inEventLoop();

        return !this.taskQueue.isEmpty();
    }

    public int pendingTasks() {
        return this.taskQueue.size();
    }

    protected void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        } else {
            if (!this.offerTask(task)) {
                this.rejectedExecutionHandler.rejected(task, this);
            }

        }
    }

    final boolean offerTask(Runnable task) {
        if (this.isShutdown()) {
            reject();
        }

        return this.taskQueue.offer(task);
    }

    protected boolean removeTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        } else {
            return this.taskQueue.remove(task);
        }
    }

    protected boolean runAllTasks() {
        boolean fetchedAll;
        do {
            fetchedAll = this.fetchFromScheduledTaskQueue();
            Runnable task = this.pollTask();
            if (task == null) {
                return false;
            }

            do {
                try {
                    task.run();
                } catch (Throwable var4) {
                    logger.warn("A task raised an exception.", var4);
                }

                task = this.pollTask();
            } while(task != null);
        } while(!fetchedAll);

        this.lastExecutionTime = ScheduledFutureTask.nanoTime();
        return true;
    }

    protected boolean runAllTasks(long timeoutNanos) {
        this.fetchFromScheduledTaskQueue();
        Runnable task = this.pollTask();
        if (task == null) {
            return false;
        } else {
            long deadline = ScheduledFutureTask.nanoTime() + timeoutNanos;
            long runTasks = 0L;

            long lastExecutionTime;
            while(true) {
                try {
                    task.run();
                } catch (Throwable var11) {
                    logger.warn("A task raised an exception.", var11);
                }

                ++runTasks;
                if ((runTasks & 63L) == 0L) {
                    lastExecutionTime = ScheduledFutureTask.nanoTime();
                    if (lastExecutionTime >= deadline) {
                        break;
                    }
                }

                task = this.pollTask();
                if (task == null) {
                    lastExecutionTime = ScheduledFutureTask.nanoTime();
                    break;
                }
            }

            this.lastExecutionTime = lastExecutionTime;
            return true;
        }
    }

    protected long delayNanos(long currentTimeNanos) {
        ScheduledFutureTask<?> scheduledTask = this.peekScheduledTask();
        return scheduledTask == null ? SCHEDULE_PURGE_INTERVAL : scheduledTask.delayNanos(currentTimeNanos);
    }

    protected void updateLastExecutionTime() {
        this.lastExecutionTime = ScheduledFutureTask.nanoTime();
    }

    protected abstract void run();

    protected void cleanup() {
    }

    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop || STATE_UPDATER.get(this) == 3) {
            this.taskQueue.offer(WAKEUP_TASK);
        }

    }

    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    public void addShutdownHook(final Runnable task) {
        if (this.inEventLoop()) {
            this.shutdownHooks.add(task);
        } else {
            this.execute(new Runnable() {
                public void run() {
                    SingleThreadEventExecutor.this.shutdownHooks.add(task);
                }
            });
        }

    }

    public void removeShutdownHook(final Runnable task) {
        if (this.inEventLoop()) {
            this.shutdownHooks.remove(task);
        } else {
            this.execute(new Runnable() {
                public void run() {
                    SingleThreadEventExecutor.this.shutdownHooks.remove(task);
                }
            });
        }

    }

    private boolean runShutdownHooks() {
        boolean ran = false;

        while(!this.shutdownHooks.isEmpty()) {
            List<Runnable> copy = new ArrayList(this.shutdownHooks);
            this.shutdownHooks.clear();
            Iterator i$ = copy.iterator();

            while(i$.hasNext()) {
                Runnable task = (Runnable)i$.next();

                try {
                    task.run();
                } catch (Throwable var9) {
                    logger.warn("Shutdown hook raised an exception.", var9);
                } finally {
                    ran = true;
                }
            }
        }

        if (ran) {
            this.lastExecutionTime = ScheduledFutureTask.nanoTime();
        }

        return ran;
    }

    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (quietPeriod < 0L) {
            throw new IllegalArgumentException("quietPeriod: " + quietPeriod + " (expected >= 0)");
        } else if (timeout < quietPeriod) {
            throw new IllegalArgumentException("timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        } else if (unit == null) {
            throw new NullPointerException("unit");
        } else if (this.isShuttingDown()) {
            return this.terminationFuture();
        } else {
            boolean inEventLoop = this.inEventLoop();

            boolean wakeup;
            int oldState;
            int newState;
            do {
                if (this.isShuttingDown()) {
                    return this.terminationFuture();
                }

                wakeup = true;
                oldState = STATE_UPDATER.get(this);
                if (inEventLoop) {
                    newState = 3;
                } else {
                    switch(oldState) {
                        case 1:
                        case 2:
                            newState = 3;
                            break;
                        default:
                            newState = oldState;
                            wakeup = false;
                    }
                }
            } while(!STATE_UPDATER.compareAndSet(this, oldState, newState));

            this.gracefulShutdownQuietPeriod = unit.toNanos(quietPeriod);
            this.gracefulShutdownTimeout = unit.toNanos(timeout);
            if (oldState == 1) {
                this.thread.start();
            }

            if (wakeup) {
                this.wakeup(inEventLoop);
            }

            return this.terminationFuture();
        }
    }

    public Future<?> terminationFuture() {
        return this.terminationFuture;
    }

    /** @deprecated */
    @Deprecated
    public void shutdown() {
        if (!this.isShutdown()) {
            boolean inEventLoop = this.inEventLoop();

            boolean wakeup;
            int oldState;
            int newState;
            do {
                if (this.isShuttingDown()) {
                    return;
                }

                wakeup = true;
                oldState = STATE_UPDATER.get(this);
                if (inEventLoop) {
                    newState = 4;
                } else {
                    switch(oldState) {
                        case 1:
                        case 2:
                        case 3:
                            newState = 4;
                            break;
                        default:
                            newState = oldState;
                            wakeup = false;
                    }
                }
            } while(!STATE_UPDATER.compareAndSet(this, oldState, newState));

            if (oldState == 1) {
                this.thread.start();
            }

            if (wakeup) {
                this.wakeup(inEventLoop);
            }

        }
    }

    public boolean isShuttingDown() {
        return STATE_UPDATER.get(this) >= 3;
    }

    public boolean isShutdown() {
        return STATE_UPDATER.get(this) >= 4;
    }

    public boolean isTerminated() {
        return STATE_UPDATER.get(this) == 5;
    }

    protected boolean confirmShutdown() {
        if (!this.isShuttingDown()) {
            return false;
        } else if (!this.inEventLoop()) {
            throw new IllegalStateException("must be invoked from an event loop");
        } else {
            this.cancelScheduledTasks();
            if (this.gracefulShutdownStartTime == 0L) {
                this.gracefulShutdownStartTime = ScheduledFutureTask.nanoTime();
            }

            if (!this.runAllTasks() && !this.runShutdownHooks()) {
                long nanoTime = ScheduledFutureTask.nanoTime();
                if (!this.isShutdown() && nanoTime - this.gracefulShutdownStartTime <= this.gracefulShutdownTimeout) {
                    if (nanoTime - this.lastExecutionTime <= this.gracefulShutdownQuietPeriod) {
                        this.wakeup(true);

                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException var4) {
                        }

                        return false;
                    } else {
                        return true;
                    }
                } else {
                    return true;
                }
            } else if (this.isShutdown()) {
                return true;
            } else if (this.gracefulShutdownQuietPeriod == 0L) {
                return true;
            } else {
                this.wakeup(true);
                return false;
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        } else if (this.inEventLoop()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        } else {
            if (this.threadLock.tryAcquire(timeout, unit)) {
                this.threadLock.release();
            }

            return this.isTerminated();
        }
    }

    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        } else {
            boolean inEventLoop = this.inEventLoop();
            if (inEventLoop) {
                this.addTask(task);
            } else {
                this.startThread();
                this.addTask(task);
                if (this.isShutdown() && this.removeTask(task)) {
                    reject();
                }
            }

            if (!this.addTaskWakesUp && this.wakesUpForTask(task)) {
                this.wakeup(inEventLoop);
            }

        }
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        this.throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        this.throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks, timeout, unit);
    }

    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        this.throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks);
    }

    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        this.throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks, timeout, unit);
    }

    private void throwIfInEventLoop(String method) {
        if (this.inEventLoop()) {
            throw new RejectedExecutionException("Calling " + method + " from within the EventLoop is not allowed");
        }
    }

    public final ThreadProperties threadProperties() {
        return this.threadProperties;
    }

    protected boolean wakesUpForTask(Runnable task) {
        return true;
    }

    protected static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }

    private void startThread() {
        if (STATE_UPDATER.get(this) == 1 && STATE_UPDATER.compareAndSet(this, 1, 2)) {
            this.thread.start();
        }

    }

    static {
        AtomicIntegerFieldUpdater<SingleThreadEventExecutor> updater = PlatformDependent.newAtomicIntegerFieldUpdater(SingleThreadEventExecutor.class, "state");
        if (updater == null) {
            updater = AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");
        }

        STATE_UPDATER = updater;
        SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(50L);
    }

    private static final class DefaultThreadProperties implements ThreadProperties {
        private final Thread t;

        DefaultThreadProperties(Thread t) {
            this.t = t;
        }

        public State state() {
            return this.t.getState();
        }

        public int priority() {
            return this.t.getPriority();
        }

        public boolean isInterrupted() {
            return this.t.isInterrupted();
        }

        public boolean isDaemon() {
            return this.t.isDaemon();
        }

        public String name() {
            return this.t.getName();
        }

        public long id() {
            return this.t.getId();
        }

        public StackTraceElement[] stackTrace() {
            return this.t.getStackTrace();
        }

        public boolean isAlive() {
            return this.t.isAlive();
        }
    }
}
