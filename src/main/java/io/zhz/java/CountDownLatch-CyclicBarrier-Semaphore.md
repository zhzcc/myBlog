## CountDownLatch

### 概念

CountDownLatch 是在 jdk1.5 的时候被引入的，位于 java.util.concurrent 并发包中，CountDownLatch 叫做闭锁，也称门闩。

CountDownLatch 是一个同步工具类，ta 允许一个或多个线程一直等待，直到其 ta 线程执行完毕后在执行

拿生活中例子来举：幼儿园老师等待家长接小孩，现有一个老师和五个小孩在教室，老师必须等待五个小孩都走了后，才能锁住教室，然后下班回家。而CountDownLatch 就是一个类似于这种一个线程等待其 ta 线程都执行完了之后，自己再执行。

### 工作原理

CountDownLatch 是通过一个计数器来实现的，计数器的初始值为线程的数量。

每当一个线程完成了自己的任务后，计数器的值就相应减 1。

当计数器的值减到 0 时，表示所有的线程都已完成任务，

然后在 CountDownLatch 上等待的线程就可以恢复执行接下来的任务

### 常用方法

```java
public CountDownLatch (int count) // CountDownLatch 接收一个 int 型参数，表示要等待的工作线程的个数

public void await() // 使用当前线程进入同步队列进行等待，直到计数器的值减少到 0或者当前线程被中断，当前线程就会被唤醒
  
public boolean await(long timeout, TimeUnit unit) // 带超时时间的 await()
    
public void countDown() // 使计数器的值减 1，如果减到 0 ，则会唤醒所有等待在这个 CountDownLatch 上的线程
    
public long getCount() // 获得 CountDownLatch 的数值，也就是计数器的值
```

### 案例演示

> 主线程等待所有的子线程执行完毕后，再执行

```java
 public class CountDownLatchDemo01 {
    private static final int THREAD_NUM = 5;

    public static void main(String[] args) {
        // 创建固定线程数量的线程池
        ExecutorService ex = Executors.newFixedThreadPool(THREAD_NUM);

        // 如果有 n 个子线程，则指定 CountDownLatch 的初始值为 n
        final CountDownLatch countDownLatch = new CountDownLatch(THREAD_NUM);

        // 提交任务到线程池
        for (int i = 0; i < THREAD_NUM; i++) {
            ex.execute(() -> {
                try {
                    System.out.println("子线程：" + Thread.currentThread().getName() + "开始执行");
                    // 模拟每个线程处理业务，耗时一秒钟
                    TimeUnit.SECONDS.sleep(1);
                    System.out.println("子线程：" + Thread.currentThread().getName() + "执行完毕");

                    // 当前线程调用此方法，则计数减一
                    countDownLatch.countDown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        try {
            // 阻塞当前线程（此例子就是 main 主线程，直到计数器的值为 0，主线程才开始处理）
            countDownLatch.await();
            System.out.println("等待子线程执行完成，主线程：" + Thread.currentThread().getName() + "开始执行，此时 CountDownLatch 的值为：" + countDownLatch.getCount());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 销毁线程池
        ex.shutdown();
    }
}
```

### 使用场景

* 开始执行前等待 N 个线程完成各自任务后，进行汇总合并
* 实行最大的并行性，同时启动多个线程：在多个线程在执行任务前首先使用 countdownlatch.await() 在这个锁上等待，只需主线程调用一次 countDown() 方法即可，类似于发令枪

### 缺点与不足

CountDownLatch 是一次性的，计数器的值只能在构造方法中初始化一次，之后不能再次对其设置值，当 CountDownLatch 使用完毕后， ta 不能再次被使用

## CyclicBarrier

### 概念

CyclicBarrier 是 jdk1.5 中引入的线程安全的组件。字面意思就是 ”可重复使用的栅栏“ 或者 ”循环栅栏“。

CyclicBarrier 可以使用一定数量的线程全部等待彼此达到共同的屏障点

拿生活中例子来举例：王者农药等待所有玩家点击准备后才可以进入房间进入英雄选择界面

## 常用方法

```java
public CyclicBarrier(int parties) // 创建一个新的 CyclicBarrier，当给定数量的线程（线程）等待 ta 时，屏障将放开，parties 表示屏障拦截的线程数量

public CyclicBarrier(int parties, Runnable barrierAction) // 创建一个新的 CyclicBarrier，当给定数量的线程（线程）等待时，屏障会放开。与第一个构造方法的区别在于：在屏障放开之前会先执行 Runnable 里面的 run 方法，由最后一个达到屏障的线程执行，方便处理更复杂的场景
    
public int await() // 每个线程使用 await()方法告诉 CyclicBarrier，已经达到了屏障，然后当前线程会被阻塞，直到 parties 个参与线程调用了 await() 方法，屏障才会放行
    
public int await(long timeout, TimeUnit unit) // 带超时时间的 await() 方法
    
public void reset() // 将屏障重置为初始化状态

public int getNumberWaiting()  // 返回目前正在屏障等待的线程数量
    
public boolean isBroken() // 方法用来了解阻塞的线程是否被中断
```

### 案例演示

```java
public class CyclicBarrierDemo {
    private static final int THREAD_NUM = 10;

    public static void main(String[] args) {
        // 创建一个循环栅栏，计数为 10
        CyclicBarrier cyclicBarrier = new CyclicBarrier(THREAD_NUM, () -> System.out.println("=====等待所有玩家准备完毕，选择英雄====="));

        // 启动 10 个子线程
        for (int i = 1; i <= THREAD_NUM; i++) {
            new Thread(new Player(cyclicBarrier),"A" + i).start();
        }
    }
}

class Player implements Runnable {

    private CyclicBarrier cyclicBarrier;

    public Player(CyclicBarrier cyclicBarrier) {
        this.cyclicBarrier = cyclicBarrier;
    }

    @Override
    public void run() {
        // 线程阻塞
        try {
            System.out.println(Thread.currentThread().getName() + "准备完毕，等待其他玩家准备");
            // 等待其他玩家准备
            cyclicBarrier.await();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 等待完毕后，选择英雄
        System.out.println(Thread.currentThread().getName() + "选择英雄");

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println(Thread.currentThread().getName() + "选择完毕");
    }
}
```

### 使用场景

* 多用于多线程计算数据，最后合并计算结果的场景

### CyclicBarrier&CountDownLatch 区别

1. CountDownLatch 的计数器只能使用一次，而 CyclicBarrier 的计数器可以使用 reset() 方法重置，可以使用多次，所以 CyclicBarrier 能够处理更为复杂的场景
2. CyclicBarrier 还提供了一些其 ta 的方法，比如 getNumberWaiting() 方法可以获得 CyclicBarrier 阻塞的线程数量，isBroken() 方法用来了解阻塞的线程是否被中断
3. CountDownLatch 就是一个线程等待其他线程完成后，自己再做某件事情，而 CyclicBarrier 则是多个线程一起等待，直到达到某一种状态，所有的线程一起同时做某件事情

## Semaphore

### 概念

Semaphore 是一个计数信号量，是 jdk1.5 引入的一个并发工具类，位于 java.util.concurrent 包中。Semaphore 字面意思就是 “信号量”，可以控制同时访问资源的线程个数

拿生活中例子来举：某个网红餐厅，一共 10 个座位，但现在来了 20 个去吃饭。那么此时只有 10 个人可以进去点餐吃饭的，而另外 10 个人只能在外等待；当 已经进餐的 10 个人中，有人结束用餐了，就又可以进去一个人点餐吃饭了，依次下去，直到所有人吃完饭

### 工作原理

Semaphore 计数信号量由一个指定的数量的 “许可” 初始化。

每调用一次 acquire()，一个许可会被调用线程取走。

每调用依次 release()，一个许可会被返还给信号量。

因此，在没有任何 release() 调用时，最多有 N 个线程能够通过 acquire() 方法，N 是该信号量初始化的许可的指定数量。这些许可其实只是一个简单的计数器

简单理解为：信号量中有很多 n 个令牌，每个线程执行任务时，需要先获取到一块令牌才能执行，那么最多就只允许 n 个线程同时执行任务，其 ta 线程阻塞在那里等待获取令牌；当 其 ta 线程执行完任务后，会把令牌还回去，这时其 ta 线程就可以获取令牌了

### 常用方法

```java
public Semaphore(int permits) // 创建一个信号量，参数 permits 表示许可数目，即同时可以允许多少线程进行访问
 
public Semaphore(int permits, boolean fair) // 创建一个信号量，参数 permits 表示许可数目，即同时可以允许多少线程进行访问。多了一个参数 fair 表示是否公平，即等待时间越久越先获取许可；默认采用的是非公平的策略; 如果为公平 Semaphore，则按照请求时间获得许可，即先发送的请求先获得许可；如果为非公平 Semaphore，则先发送的请求未必获得许可，这有助于提高程序的吞吐量，但是有可能导致某些请求始终获取不到许可
    
public void acquire() // 用于获取一个许可，若无许可能够获得，则会一直等待，直到获得许可
  
public void acquire(int permits) // 用于获取 permits 个许可，若无许可获得，则会一直等待，直到获得许可
    
public void release() // 用于释放一个许可。注意：在释放许可之前，得先获得许可
    
public void release(int permits) // 用于释放 permits 个许可。注意：在释放许可之前，得先获得许可
    
public boolean tryAcquire() // 尝试获取一个许可，若获取成功，则立即返回 true，若失败，则立即返回false
    
public boolean tryAcquire(long timeout, TimeUnit unit) // 尝试获取一个许可，若在指定的时间内获取成功，则立即返回 true，否则立即返回 false
    
public boolean tryAcquire(int permits) // 尝试获取 permits 个许可，若获取成功，则立即返回 true，若失败则返回 false
    
public boolean tryAcquire(int permits, long timeout, TimeUnit unit) // 尝试获取 permits 个许可，若在指定的时间内获取成功，则立即返回 true，若失败返回false
    
public int availablePermits() // 获取信号量中当前可用许可数目
```

### 案例演示

> 网红饭馆排队就餐案例

```java
public class SemaphoreDemo {

    /**
     * 网红餐馆共 10 个位置
     */
    private static final int MAX_AVAILABLE = 10;

    public static void main(String[] args) {
        // 只有 10 个位置，就是说同时只能允许 10 个人进行就餐
        Semaphore semaphore = new Semaphore(MAX_AVAILABLE);

        // 模拟 20 个人去就餐
        for (int i = 1; i <= 20; i++) {
            new Restaurant(semaphore,i).start();
        }
    }
}

class Restaurant extends Thread {
    /**
     * 信号量
     */
    private Semaphore semaphore;

    /**
     * 第几个子线程
     */
    private int num;

    public Restaurant(Semaphore semaphore, int num) {
        this.semaphore = semaphore;
        this.num = num;
    }

    @Override
    public void run() {
        try {
            // 第一个人进入餐馆，获得点餐许可
            semaphore.acquire();
            System.out.println("第 " + num + " 个人进入餐馆");
            Thread.sleep((int)(Math.random()*5000)+1);
            System.out.println("第 " + num + " 个人离开餐馆");
            // 离开餐馆，释放点餐许可
            semaphore.release();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 使用场景

* 信号量 Semaphore 可以用来做限流，控制同时访问资源的线程数量或者用户数量等