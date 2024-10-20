# synchronized 基本概念

## 概览

synchronized 是一个同步关键字，在某些多线程场景下，如果不进行同步会导致共享数据不安全，而 synchronized 关键字就可以用于代码同步

synchronized 主要有 3 种使用形式：

* 修饰普通方法：锁的对象是当前实例对象
* 修饰静态同步方法：锁的对象是当前的类的 Class 字节码对象
* 修饰同步代码块：锁的对象是 synchronized 后面括号里配置的对象，可以是某个对象，也可以是某个类的 .class 对象

> 常见问题：
>
> synchronized 锁的粒度
>
> synchronized 工作原理
>
> synchronized 锁升级过程以及触发条件
>
> synchronized 锁对象存放在哪里
>
> 为什么 GC 年龄最大是 15
>
> synchronized 与 RenentrantLock区别
>
> 同一代码使用 synchronized 和 RenentrantLock 有什么区别
>
> synchronized 百分百保证线程安全吗

## synchronized 的特性

1）原子性：指的是一次或多次操作中，要么所有操作都执行并且不受其 ta 干扰而中断；要么所有的操作都不执行；

​	synchronized 通过控制 Object Monitor 的访问控制权 保证原子性

2）可见性：指一个线程对共享变量进行了修改，另一个线程可以立即读取得到修改后的最新值

synchronized 可见性是通过内存屏障实现的，按可见性划分，内存屏障分为：

* Load 屏障：执行 refresh，从其他处理器的高速缓冲、主内存，加载数据到自己的高速缓冲，保证数据是最新的
* Store 屏障：执行 flush 操作，自己处理器更新的变量值，刷新到高速缓冲、主内存中

> 获取锁时，会清空当前线程工作内存中共享变量的副本值，重新从主内存中获取变量最新的值
>
> 释放锁时，会将当前内存的值重新刷新到主内存
>
> ```java
> int a = 0;
> synchronized (this) { // 编译之后的字节码中，进入同步块，会生成 monitorenter
>     // Load 内存屏障
>     int b = a; // 读，通过 load 内存屏障，强制执行 refresh，保证读到最新的
>     a = 10; // 写，释放锁时会通过 Store，强制 flush 到高速缓存或内存
> } // 退出同步代码块,会生成 monitorexit
> // Store 内存屏障
> ```

3）有序性：指程序中代码的执行顺序，Java 在编译时和运行时会对代码进行优化，会导致程序最终的执行顺序不一定就是我们编写代码时的顺序

例如：instance = new Singleton() 实例化对象的语句分为以下三步：

1. 分配对象的内存空间
2. 初始化对象
3. 设置实例对象指向刚分配的内存地址

> 上述第二步操作需要依赖第一步，但是第三部操作不依赖第二步，所以执行顺序可能为：1 -> 2 -> 3，也可能为 1 -> 3 -> 2，当执行顺序为 1 -> 3 -> 2 时，可能实例对象还没正确初始化，我们直接拿到使用的时候，可能会报错

synchronized 的有序性是依靠内存屏障实现的

按照有序性，内存屏障可分为：

* Acquire 屏障：load 屏障之后，加 Acquire 屏障。ta 会禁止同步代码块内的读操作，和外面的读写操作发生指令重排
* Release 屏障：禁止写操作，和外面的读写操作发生指令重排

在 monitorenter 指令和 Load 屏障之后，会加一个 Acquire 屏障，这个屏障的作用是禁止同步代码块里面的读操作和外面的读写操作之间发生指令重排，在 monitorexit 指令前加一个 Release 屏障，也是禁止同步代码块里面的写操作和外面的读写操作之间发生重排序，如下：

```java
int a = 0;
synchronized (this) { // monitorenter
    // Load 内存屏障
    // Acquire 屏障，禁止代码块内部的读，和外面的读写发生指令重排
    int b = a;
    a = 10; // 注意：内部还是会发生指令重排
    // Release 屏障，禁止写，和外面的读写发生指令重排
} // monitorexit
// Stroe 内存屏障
```

4）可重入行：指的是一个线程可以多次执行 synchronized，重复获取同一把锁

例如：

```java
public class RenentrantDemo {
    // 锁对象
    private static Object obj = new Object();
    
    public static void main (Stirng[] args) {
        // 自定义 Runnable 对象
        Runnable runnable = () -> {
            // 使用嵌套的同步代码块
            synchronized (obj) {
                System.out.println(Thread.currentThread().getName() + "第一次获取锁资源 ...");
                 synchronized (obj) {
                	System.out.println(Thread.currentThread().getName() + "第二次获取锁资源 ...");
					synchronized (obj) {
                		System.out.println(Thread.currentThread().getName() + "第三次获取锁资源 ...");
                 	}
                 }
            }
        }
        new Thread(runnable,"t1").start();
    }
}
```

运行结果：

```java
t1第一次获取锁资源...
t2第一次获取锁资源...
t3第一次获取锁资源...
```

## synchonized 的使用以及通过反汇编分析其原理

### 修饰代码块

```java
public class SynchronizedDemo01 {
    // 锁对象
    private static Object obj = new Object();
    
    public static void main(String[] args) {
        synchronized(obj) {
            System.out.println("execute main() ...");
        }
    }
}
```

使用 `javap -p -v .\SynchronizedDemo01.class`  命令对字节码进行反汇编，查看字节码指令：

![image-20241019141338546](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191413647.png)

#### monitorenter 指令

官网对 monitorenter 指令的介绍，说每一个对象都会和一个监视器对象 monitor 关联，监视器被占用时会被锁住，其他线程无法来获取该 monitor。当 JVM 执行某个线程的某个方法内部的 monitorenter 时，ta 会尝试去获取当前对象对应的 monitor 的所有权。大概过程如下：

1. 若 monitor 的进入数为 0，线程可以进入 monitor，并将monitor 的进入数置为 1，当前线程成为 monitor 的 owner（拥有这把锁的线程）
2. 若线程已拥有 monitor 的所有权，允许 ta 重入 monitor，则进入 monitor 的进入数加 1（记录线程拥有锁的次数）
3. 若其他线程已经占有 monitor 的所有权，那么当前尝试获取 monitor 的所有权的线程会被阻塞，直到 monitor 的进入数变为 0，才能重新尝试获取 monitor 的所有权

#### monitorexit 指令

官网对  monitorexit 指令的介绍，就是说能执行 monitorexit 指令的线程一定是拥有当前对象的 monitor 的所有权的线程；执行 monitorexit 时会将 monitor 的进入数减 1，当 monitor 的进入数减为 0 时，当前线程退出。

> 为什么字节码中存在两个 monitorexit 指令？
>
> 其实第二个 monitorexit 指令，是在程序发生异常时用到的，也就是说明了 synchronized 在发生异常时，会自动释放锁

ObjectMonitor 对象监视器结构如下：

```java
ObjecctMonitor () {
    _header	 				 = NULL; 	// 锁对象的原始对象头
    _count	  				 = 0;    	// 抢占当前锁的线程数量
    _waiters				 = 0;		// 调用 wait 方法后等待的线程数量
    _recursions	   			 = 0; 		// 记录锁重入次数
    _object		  			 = NULL; 
    _owner		   			 =NULL;		// 指向持有 ObjectMonitor 的线程
    _WaitSet 		 		 = NULL; 	// 处于 wait 状态的线程队列，等待被唤醒
    _WaitSetLock	 		 = NULL; 
    _Responsible	  		 = NULL; 
    _succ		 		   	 =  NULL;
    _cxq 					 = NULL;
    FreeNext 				 = NULL;
    _EntryList 				 = NULL; 	// 等待锁的线程队列
    _SpinClock 				 = 0;
    OwnerIsThread 			 = 0;
    _previous_owner_tid		 = 0;
}
```

### 修饰普通方法

```java
public class SynchronizedDemo02 {
    public static void main (Sting[] args) {
        // 修饰普通方法
        public synchronized void add() {
            System.out.pringln("add...");
        }
    }
}
```

使用 `javap -p -v ./SynchronizedDemo02.class` 命令对字节码进行反汇编，查看字节码指令：

![image-20241019143750420](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191437461.png)

如上图，可以看到同步方法在反汇编后，不再是通过插入 monitorentry 和 monitorexit 指令，而是会增加 ACC_SYNCHRONIZED 标识隐式实现的，如果方法表结构（method_info Structure）中的 ACC_SYNCHORNIZED 标识被设置，那么线程在执行方法前会先获取对象的 monitor 对象，如果获取成功则执行方法代码；如果 monitor 对象已经被其 ta 线程获取，那么当前线程被阻塞。

### 修饰静态方法

```java
public class SynchronizedDemo03 {
    public static void main (String[] args) {
        add();
    }
     
    // 修饰静态方法
    public synchronized static void add() {
        System.out.println("add...");
    }
}
```

使用 `javap -p -v .\SynchronizedDemo03.class` 命令对字节码进行反汇编，查看字节码指令：

![image-20241019144539868](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191445904.png)

修饰静态同步方法，锁对象是整个类的 class 字节码这个类的模版的对象，锁的力度大，

而修饰普通同步方法锁对象是当前实例对象

## synchronized 锁对象存在那里

之前对对象的内存布局介绍，一个对象，包括对象头、实例数据、对其填充。而对象头又包括 mark word 标记字、类型指针、数组长度（只有数组对象才有）。在 mark word 标记字中，有一块区域主要存放关于锁的信息。

存在锁对象头的 Mark Word 标记中，如下图：

![image-20241019145130464](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191451526.png)

## synchronized 与 lock 的区别

| 区别 | synchronized           | lock                                  |
| ---- | ---------------------- | ------------------------------------- |
| 1    | 关键字                 | 接口                                  |
| 2    | 自动释放锁             | 必须手动调用 unlock() 方法释放锁      |
| 3    | 不能知道线程是否拿到锁 | 可以知道线程是否拿到锁（tryLock()）   |
| 4    | 能锁住方法和代码块     | 只能锁住代码块                        |
| 5    | 读、写操作都阻塞       | 可以使用读锁，提高多线程读效率        |
| 6    | 非公平锁               | 通过构造方法可以指定是公平锁/非公平锁 |

## 总结

1. synchronized 修饰代码块时，通过再生产的字节码指令中插入 monitorenter 和 monitroexit 指令来完成对对象监视器锁的获取和释放
2. synchronized 修饰普通方法和静态方法的时候，通过在字节码中的方法信息中添加 ACC_SYNCHRONIZED 标识，线程在执行方法前会先去获取对象的 monitor 对象，如果获取成功则执行方法代码，执行完毕后释放 monitor 对象
3. synchronized 修饰代码块，锁住的对象是代码块中的对象；
   修饰普通方法的时候，锁的对象是当前对象 this；
   修饰静态方法的时候，锁的对象就是当前类的 Class 字节码对象（类对象）
4. 使用 synchronized 修饰实例对象时，如果一个线程正在访问实例对象的一个 synchronized 方法时，其 ta 线程不仅不能访问该 synchronized 方法，该对象的其 ta synchronized 方法也不能访问，因为一个对象只有一个监视器锁对象，但是其 ta 线程可以访问该对象的非 synchronized 方法
5. 线程 A 访问实例对象的非 static synchronized 方法时，线程 B 也可以同时访问实例对象的 static synchronized 方法，因为前者获取的是实例对象的监视器锁，而后者获取的是类对象的监视器锁，两者不存在互斥关系

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191507433.png" alt="image-20241019150731236" style="zoom:150%;" />

# synchronized 锁升级

## 概览



## 无锁

为了优化 synchronized 锁的效率，在 JDK6 中，HotSpot 虚拟机开发团队提出了锁升级的概念，包括偏向锁、轻量级锁、重量级锁等，锁升级指的就是 “ 无锁 -> 偏向锁 -> 重量级锁 ”。

synchronized 同步锁相关信息保存到锁对象的对象头里面的 Mark Word 中，锁升级功能主要是依赖 Mark Work 中锁标志位和是否偏向锁标志位来实现的。

![image-20241019152628698](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191526737.png)

从上图可以看到，无锁对应的锁标志位是 “01”，是否偏向锁标志是 “0”

```java
public class NoLock {
    public static void main(String[] args) {
        Object objLock = new Object();
        // 需要注意，只有调用了 hashCode(),对象头中的 MarkWord 才会保存对应的 hashCode 值，否则全部是 0
        System.out.println("10 进制：" + objLock.hashCode());
        System.out.println("2 进制：" + Integer.toBinaryString(objLock.hashCode());
        System.out.println("16 进制：" + Integer.toHexString(objLock.hashCode());
        System.out.println(ClassLayout.parseInstance(objLock).toPrintable);
    }
}
```

![image-20241019153300333](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191533393.png)

Mark Word 对象头总共占 8 个字节，共 64 位，按照上图中 “ 1 -> 8”，也就是从后面往前面拼接起来：

![image-20241019153539669](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191535703.png)

## 偏向锁

HotSpot 作者经过研究实践发现，在大多数情况下，锁不仅不存在多线程竞争，而且总是由同一线程多次获取，为了让线程获得锁的代价更低，引入了偏向锁。

> 偏向锁的 “偏”，ta 的意思是锁会偏向于第一个获得 ta 的线程，会在对象头（Mark Word 中）记录锁偏向的线程 ID，以后线程进入和退出同步块时只需要检查是否为偏向锁、锁标志位以及 ThreadID 即可

如下图，是偏向锁对象头 Mark Word 布局：

![image-20241019154010829](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191540865.png)

### 事例演示

```java
public class BiasedLockDemo01 {
    public static void main (Sting[] args) {
        Object objLock = new Object();
        new Threard(()->{
            synchronized (objLock) {
                System.out.println(ClassLayout.parseIntance(objLock).toPrintable());
            }
        }, "t1").start();
    }
}
```

![image-20241019154238374](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191542409.png)

如上，markword 的倒数三位是 000，根据前面的图，000 标识是轻量级锁，此时只有一个线程访问，为什么数出来的不是偏向锁标识 101 呢？

原因在于偏向锁在 Java6 之后是默认启用的，但在应用程序启动几秒钟（默认延迟 4s）之后才会激活，可以使用 -XX:BiasedLockingStartupDelay=0参数关闭延迟，让其在程序启动立刻启动。如过为了演示，可以再程序代码中添加休眠 5 秒，等待偏向所激活

下面添加运行时 JVM 参数，再次启动程序，观察内存布局：

![image-20241019154615046](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191546079.png)

可以看到关闭偏向锁激活时间，当前锁就是偏向锁了

### 偏向锁原理

![image-20241019154729875](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191547922.png)

**在偏向锁第一次被线程拥有的时候，在偏向锁的MarkWord中，有一块区域用来记录偏向线程的ID。**

![image-20241019154839429](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191548473.png)

**注意，偏向锁只有遇到其它线程尝试竞争偏向锁时，持有偏向锁的线程才会释放锁，线程不会主动释放偏向锁的。**

### 偏向锁的撤销

![image-20241019155130515](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410191551613.png)

> 偏向锁的产生是由于实践中大部分时间锁不仅不存在竞争，而且总是由同一线程反复获得。为了降低线程反复获得同一锁时的代价，产生了偏向锁的概念。 
>
> 偏向锁的降低代价的点在于线程不会主动释放锁 对象头中存储了锁偏向的线程 ID,当尝试获取锁的线程 ID 与锁对象的对象头 Mark Word 中存储的偏向线程 ID 一致时，就认为该线程已持有锁，直接进入同步代码块。 
>
> 如果不一致的话，会进行 CAS 尝试将存储的偏向线程 ID 修改为自己 尝试成功的话线程可以获得锁执行同步代码块，同时锁仍为偏向锁 尝试失败的话即发生竞争，开始自旋同时锁升级为轻量级锁 
>
> 假设有线程 t1、线程 t2 和线程 t3，此时锁对象的 Mark Word 中无记录(null),匿名偏向状态
>
> t1 访问了锁对象，经过检查，发现发现 Mark Word 中的线程 ID 与自己不匹配，执行 CAS 操作修改为自己 
>
> t1 再次访问了锁对象，经过检查，发现 Mark Word 中的线程 ID 与自己匹配，直接进入同步代码块
>
> t2 访问了锁对象，经过检查，发现 Mark Word 中的线程 ID 与自己不匹配，偏向的是 t1,于是执行 CAS 操作：在全局安全点时，暂停了线程 t1 检查状态，发现 t1 已经停止(执行完毕或异常终止)，,Mark Word 更改为 t2 的线程 ID 
>
> t2 再次访问了锁对象，经过检查，发现 Mark Word 中的线程 ID 与自己匹配，直接进入同步代码块 
>
> t3 访问了锁对象，经过检查，发现 Mark Word 中的线程 ID与自己不匹配，偏向的是 t2,于是执行 CAS 操作：在全局安全点时，暂停了线程 t2 检查状态，发现 t2 仍然活跃，t3 修改失败,锁升级为轻量级锁，升级后锁仍然由 t2 持有; t2 被唤醒继续执行, t3 保持等待，之后按照轻量级锁执行。

## 轻量级锁

### 什么是轻量级锁

轻量级锁指的是存在多线程竞争，但是任意时刻最多只允许一个线程竞争获得锁，即不存在锁竞争太激烈的情况。

轻量级锁情况如下，线程不会发生阻塞：

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410201928786.png" alt="image-20241020192800750" style="zoom:200%;" />

### 为什么引入轻量级锁

轻量级锁考虑的是竞争锁对象的线程不多，而且线程持有锁的时间也不长的场景。

因为阻塞线程需要 CPU 从用户态转换到内核态，代价比较大，如果刚刚阻塞不久这个锁就被释放了，那这个代价就有点得不偿失，所以这时不如直接不阻塞这个线程，让 ta 自旋在这，等待锁的释放

### 轻量级锁的升级时机

1. 关闭偏向锁的功能
   使用 -XX:UseBiasedLocking 参数关闭偏向锁，此时默认进入轻量级锁
2. 多个线程竞争偏向锁
   偏向锁状态下，由于别的线程尝试竞争偏向锁，并且 CAS 更新 MarkWord 中线程 ID 失败，此时发生【偏向锁 -> 轻量级锁】升级

### 轻量级锁的演示

```java
public class LightWeightLockDemo01 {
    public static void main(String[] args) {
         // -XX:-UseBiasedLocking 关闭偏向锁，默认进入轻量级锁
        Object objLock = new Object();
        new Thread(()->{
            synchronized (objLock) {
                System.out.println(ClassLayout.parseInstance(objLock).toPrintable());
            }
        },"t1").start();
    }
}

public class LightWeightLockDemo02 {
     public static void main(String[] args) {
          Object objLock = new Object();
          String printable = ClassLayout.parseInstance(objLock).toPrintable();
         // 无锁
          System.out.println(printable);
          new Thread(()->{
            synchronized (objLock) {
                try {
                    
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
                System.out.println(ClassLayout.parseInstance(objLock).toPrintable());
            }
        },"t1").start();
         Thread.sleep(10);
         printable = ClassLayout.parseInstance(objLock).toPrintable();
         // 轻量级锁
           System.out.println(printable);
     }
}
```

### 轻量级锁的原理

#### 轻量级锁的加锁

1. JVM 会在当前线程的栈帧中建立一个名为锁记录（Lock Record）的空间，用于存储锁对象目前的 Mark Word 的拷贝（Displaced Mark Word）。若一个现场获得锁时发现是轻量级锁，ta 会将对象的 Mark Word 复制到栈帧中的锁记录 Lock Record 中（Displaced Mark Work 里面）
2. 线程尝试利用 CAS 操作将对象的 Mark Word 更新为指向 Lock Record 的指针，如果成功表示当前线程竞争到锁，则将标志位变为 00，执行同步操作；如果失败，标识 Mark Word 已经被替换为了其 ta 线程的锁记录，说明在与其 ta 线程抢占竞争锁，当前想爱你成尝试使用自旋来获取锁

#### 轻量级锁的释放

轻量级锁的释放也是通过 CAS 操作来进行的，当前线程使用 CAS 操作将 Displaced Mark Word 的内存赋值回锁对象的 MarkWord 中，如果 CAS 操作替换成功，则说明释放锁成功；如果 CAS 自旋多次还是替换失败的话，说明有其 ta 线程尝试获取该锁，则需要轻量级锁膨胀升级为重量级锁

### 轻量级锁的升级流程

![image-20241020195533428](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410201955558.png)

### 轻量级锁的优缺点

* 优点：在多线程交替执行同步块的情况下，可以避免重量级锁引起的性能消耗
* 缺点：如果长时间自旋后，还没竞争到锁，将会过度耗费 CPU，即 CPU 空转

## 重量级锁

### 什么是重量级锁

当大量的线程都在竞争同一把锁时，这个时候加的锁，就是重量级锁

![image-20241020204514208](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410202045252.png)

#### ObjectMonitor 监视器对象

```java
ObjecctMonitor () {
    _header	 				 = NULL; 	// 锁对象的原始对象头
    _count	  				 = 0;    	// 抢占当前锁的线程数量
    _waiters				 = 0;		// 调用 wait 方法后等待的线程数量
  * _recursions	   			 = 0; 		// 记录锁重入次数
    _object		  			 = NULL; 
  * _owner		   			 =NULL;		// 指向持有 ObjectMonitor 的线程
  * _WaitSet 		 		 = NULL; 	// 处于 wait 状态的线程队列，等待被唤醒
    _WaitSetLock	 		 = 0; 
    _Responsible	  		 = NULL; 
    _succ		 		   	 =  NULL;
    _cxq 					 = NULL;
    FreeNext 				 = NULL;
  * _EntryList 				 = NULL; 	// 等待锁的线程队列
    _SpinFreq				 =0;
    _SpinClock 				 = 0;
    OwnerIsThread 			 = 0;
    _previous_owner_tid		 = 0;
}
```

### 重量级锁的演示

```java
public Class HightweightLockDemo {
    public static void main(String[] args){
        Object objLock = new Object();
        new Thread(()->{
            synchronized (objLock) {
                System.out.pringln(ClassLayout.parseInstance(objLock).toPrintable);
            }
        },"t1").start();
        
        new Thread(()->{
            synchronized (objLock) {
                System.out.pringln(ClassLayout.parseInstance(objLock).toPrintable);
            }
        },"t2").start();
    }
}
```

可以使用 `javap -p -v .\HightweightLockDemo.class` 查看反编译指令

![image-20241020205511592](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410202055653.png)
![image-20241020205530350](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410202055394.png)

### 锁的优点对比

| 锁的类型 | 优点                                                         | 缺点                                                         | 适合场景                           |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ---------------------------------- |
| 偏向锁   | 加锁和解锁不需要额外的消耗，和执行非同步方法相比仅存在纳秒级别的差距 | 如果线程间存在锁竞争，会带来额外的锁撤销的消耗               | 适用于只有一个线程访问同步块场景   |
| 轻量级锁 | 竞争的线程不会阻塞，提高了程序的响应速度（不需要内核态、用户态的转换） | 如果始终得不到锁竞争的线程，使用自旋会消耗 CPU，导致 CPU 空转 | 追求响应时间，同步块执行速度非常快 |
| 重量级锁 | 线程竞争不使用自旋，不会消耗 CPU                             | 线程阻塞，响应时间缓慢                                       | 追求吞吐量，同步块执行时间较长     |

