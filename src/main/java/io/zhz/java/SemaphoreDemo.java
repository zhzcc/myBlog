package io.zhz.java;

import java.util.concurrent.Semaphore;

/**
 * @Author: ZhangHuazhi
 * @CreateTime: 2024-10-15  22:33
 * @Description: TODO
 * @Version: 1.0
 */
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
