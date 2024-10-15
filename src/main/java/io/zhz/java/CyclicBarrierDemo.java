package io.zhz.java;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * @Author: ZhangHuazhi
 * @CreateTime: 2024-10-15  21:48
 * @Description: TODO
 * @Version: 1.0
 */
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
