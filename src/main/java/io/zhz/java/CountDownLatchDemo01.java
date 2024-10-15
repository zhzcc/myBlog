package io.zhz.java;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @Author: ZhangHuazhi
 * @CreateTime: 2024-10-15  21:17
 * @Description: CountDownLatch 示例一
 * @Version: 1.0
 */
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
