## Redis 为什么快

* <font color='red'>基于内存实现</font>：Redis 将数据存储在内存中，读写操作不会受到磁盘 IO 速度限制；

  CPU 不是 Redis 的瓶颈，Redis 的瓶颈在于机器内存的大小或者网络带宽

* <font color='red'>I/O多路复用模型</font>的使用：Redis 线程不会阻塞在某一个特定的客户端请求处理上；
  可以同时和多个客户端连接并处理请求，从而提升了并发性

* <font color='red'>采用单线程模型</font>：Redis 的网络 IO 以及键值对指令读写是由一个线程来执行的；
  对于 Redis 的持久化、集群数据同步、异步删除等都是其他线程执行的
  单线程避免了线程切换和竟态产生的消耗，对于服务端开发来说，锁和线程切换 通常为性能累赘

* <font color='red'>高效的数据结构</font>：不同数据类型使用不同的数据结构得以提升速度

## 数据结构

### 数据类型

* string 字符串
* list 列表
* hash 哈希
* set 集合
* zset 有序集合

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410131558752.png" alt="image-20241010212152369" style="zoom:67%;" />

|          | string                                                       | list                                                         | hash                                                         | set                                                          | zset                                                         |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 概念     | 1、可以存储任意类型的数据，比如文本、数字、图片或者序列化对象<br />2、一个 string 类型的键最大可以存储 512 MB 的数据 | 1、一个有序的字符串列表，ta 按照插入顺序排序，并且支持在两端插入或删除元素<br />2、一个 list 类型的键最多可以存储 2^32-1 个元素 | 1、一个键值对集合，ta 可以存储多个字段和值，类似于java 的 map 对象<br />2、一个 hash 类型的键最多可以存储 2^32-1 个字段 | 1、set 是一个无序的字符串集合，ta 不允许元素重复<br />2、一个 set 类型的键最多可以存储 2^32-1 个元素 | 1、redis 中的 zset 是一种有序集合类型，ta 可以存储不重复的字符串元素，并且给每个元素赋予一个排序权重值（score）；redis 通过权重值来为集合中的元素进行从小到大的排序<br />2、zset 的成员是唯一的，但权重值可以重复<br />3、一个 zset 类型的键最多可以存储 2^32-1 个元素 |
| 底层实现 | string 类型的底层实现是 <font color ='red'>SDS</font>， ta 是一个<font color = 'red'>动态字符串结构</font>，由<font color = 'red'>长度、空闲空间和字节数</font>据组三部分组成<br />SDS 有 3 中编码类型：<br />1、embstr：占用64 Bytes 的空间，存储 44 Bytes 的数据<br />2、raw：存储大于 44 Bytes 的数据<br />3、int：存储整数类型<br />embstr 和 raw 存储字符串数据，int 存储整型数据 | redis3.2 以后，list 类型的底层实现只有一种结构：quicklist<br /><br />分析：<br />1、在 <font color='red'>Redis 3.2</font>之前，list 使用的是 <font color='red'>linkedlist 和 ziplist</font>；在 <font color='red'>Redis3.2-Redis7.0</font>之间，list 使用的是 <font color='red'>quickList</font>，是 linkedlist 和 ziplist 的结合；在 <font color='red'>Redis7.0</font> 之后，list 使用的也是 <font color='red'>quickList</font> ，只不过<font color='red'>将 ziplist 转换为 listpack</font> ，ta <font color='red'>是 listpack、linkedlist 结合版</font> <br />2、ziplist（压缩列表）：当列表的元素个数小于 <font color='red'>list-max-ziplist-entries</font>  配置，同时列表中每个元素的值都小于 <font color='red'>list-max-ziplist-value</font>  配置时使用<br />3、linkedlist（链表）：当列表类型无法满足 <font color='red'>ziplist</font>  的条件时，Redis 会使用 <font color='red'>linkedlist</font>  作为列表的内部实现 | <font color='red'>hash</font>  类型的底层实现有三种：<br />1、<font color='red'>ziplist</font> ：压缩列表，当 hash 达到一定的阈值时，会自动转换为 <font color='red'>hashtable</font>  结构<br />2、<font color='red'>listpack</font> ：紧凑列表，在 redis7.0 之后，<font color='red'>listpack</font>  正式取代 ziplist；同样的，当 <font color='red'>hash</font>  达到一定的阈值<font color='red'>时，会自动转换为</font>  <font color='red'>hashtable </font> 结构<br />3、<font color='red'>hashtable</font> ：哈希表，类似 map<br /><br />分析：<br />1、<font color='red'>ziplist</font> （压缩表）：当哈希类型元素小于 <font color='red'>hash-maxx-ziplist-entries</font> 配置，同时所有值都小于 hash-max-ziplist-value 配置时使用；<br /><font color='red'>ziplist</font>  使用更加紧凑的结构实现多个元素的连续存储，在节省内存方面比 <font color='red'>hashtable</font>  更有优势<br />2、<font color='red'>hashtable</font> （哈希表）：当哈希类型无法满足 <font color='red'>ziplist</font>  的条件时，Redis 会使用 hashtable 作为哈希的内部实现；原因是 <font color='red'>ziplist</font>  的读写效率下降，而 <font color='red'>hashtable </font> 的读写的复杂度为 <font color='red'>O（1）</font> | set 类型的底层实现有两种：<br />1、intset，整数集合<br />2、hashtable 哈希表；哈希表和 hash 类型的哈希表相同，ta 将元素存储在一个数组中，并通过哈希函数计算元素在数组中的索引<br /><br />分析：<br />1、在 Redis7.2 之前，set 使用的是 intset 和 hashtable；在 Redis7.2 之后，set 使用的是 intset、listpack、hashtable<br />2、intset（整数集合）：当集合中的元素都是整数且元素个数小于 set-max-intset-entries 配置时使用<br />3、hashtable（哈希表）：当集合类型无法满足 intset 的条件时，Redis 使用 hashtable 作为集合的内部实现 | 1、ziplist（redis7.0前）和 listpack（redis7.0后）<br />2、skiplist<br /><br />分析：<br />1、当有序集合的元素个数小于 zset-max-ziplist-entries（默认为 128 个），并且每个元素成员的长度小于 zset-max-ziplist-value（默认为 64 字节）时，使用压缩列表作为有序集合的内部实现；<br />每个集合元素由两个紧挨在一起的两个压缩列表节点组成，其中第一个节点保存元素成员，第二个节点保存元素的分支；<br />压缩列表中的元素按照分数从小到大一次紧挨着排列，有效减少了内存空间的使用<br />2、当有序集合的元素大于等于 zset-max-ziplist-entries（默认为 128 个），或者每个元素成员的长度大于等于 zset-max-ziplist-value（默认为 64 字节）时，使用跳跃表作为有序集合的内部实现；<br />在跳跃表中，所有元素按照从小到大的顺序排序；<br />跳跃表的节点中的 object 指针指向元素成员的字符串对象，score 保存元素的分数；<br />通过跳跃表，Redis 可以快速d e 对有序集合进行分数范围、排名等操作<br /><br />3、当哈希表中，为有序集合创建了一个从元素成员到元素分数的映射：键值对中的键指向元素成员的字符串对象，键值对中的值保存了元素的分数，通过哈希表，Redis 可以快速查找指定元素的分数；<br />虽然有序集合同时使用跳跃表和哈希表，但是着两种数据结构都是用指针共享元素的成员和分数，不会额外的内存浪费 |
| 应用场景 | 1、<font color ='red'>缓存数据</font>，提高访问速度和降低数据库压力<br />2、<font color ='red'>计数器</font>，利用 <font color ='red'>incr 和 decr</font> 命令实现原子性的加减操作<br />3、<font color = 'red'>分布式锁</font>，利用 <font color ='red'>setnx</font> 命令实现互斥访问<br />4、<font color ='red'>限流</font>，利用 <font color ='red'>expire</font> 命令实现时间窗口内的访问控制 | 1、<font color ='red'>消息队列</font>，利用 <font color ='red'>lpush 和 rpop</font> 命令实现生产者消费者模式<br />2、<font color='red'>最新消息</font>，利用 <font color='red'>lpush 和 ltrim</font> 命令实现固定长度的时间线<br />3、<font color = 'red'>历史记录</font>，利用 <font color ='red'>lpush 和 lrange</font> 命令实现浏览记录或者搜索记录 | hash 类型的应用场景主要是存储对象，比如：<br />1、用户信息，利用 hset 和 hget 命令实现对象属性的增删改查<br />2、购物车，利用 hincrby 命令实现商品数量的增减<br />3、配置信息，利用 hmset 和 hmget 命令实现批量设置和获取配置项 | 1、<font color = 'red'>去重</font>，利用 <font color ='red'>sadd 和 scard</font> 命令实现元素的添加和计数<br />2、<font color = 'red'>交集，并集，差集</font>，利用 <font color='red'>sinter，sunion 和 sdiff</font> 命令实现集合间的运算<br />3、<font color ='red'>随机抽取</font>，利用 <font color ='red'>srandmember</font> 命令实现随机抽奖或者抽样 | 1、排行榜，利用 zadd 和 zrange 命令实现分数的更新和排名的查询<br />2、延时队列，利用 zadd 和 zpopmin 命令实现任务的添加和执行，并且可以定期 de 获取已经到期的任务<br />3、访问统计，可以使用 zset 来存储网站或者文章的访问次数，并且可以按照访问量进行排序和筛选 |

![image-20241010214801846](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410131559314.png)

### 为什么加入 listpack？

> 在 redis7.2 之前，sds 类型的数据会直接放入到编码结构为 hashtable 的set 中
>
> * 其中，sds 其实就是 redis 中的 string 类型
>
> 在 redis7.2 之后，sds 类型的数据，首先会使用 listpack 结构，当 set 达到一定的阈值时，才会自动转换为 hashtable。添加 listpack 结构是为了提高内存利用率和操作效率，因为 hashtable 的空间开销和碰撞概率都比较高

## 内存机制

### 内存回收策略

Redis 的内存回收机制主要表现为以下两方面：

* 删除到达过期时间的键对象
* 内存使用达到 Maxmemory 上限，触发内存溢出控制策略

删除过期对象：Redis 所有的键都可以设置过期属性，内部保存在过期字典中

* <font color='red'>惰性删除</font>：当客户端读取带有超时属性键时，如果已经超过键设置的过期时间，将执行删除操作，并返回空
* <font color ='red'>定时任务删除</font>：Redis 内部维护了一个定时任务，默认每秒运行 10 次

### 内存溢出策略

当 Redis 所有内存达到 Maxmemory 上限时会触发相应的溢出策略：

| name            | describe                                                     |
| --------------- | ------------------------------------------------------------ |
| noeviction      | 默认策略，不会删除任何数据，拒绝所有写入操作并返回客户端错误信息，此时 Redis 只响应读操作 |
| volatile-lru    | 根据 LRU 算法，删除设置了超时属性的键<br />如果没有可删除的键对象，回退到 noeviction 策略 |
| allkeys-lru     | 根据 lru 算法删除键，不管数据有没有设置超时属性              |
| allkeys-random  | 随机删除所有键                                               |
| volatile-random | 随机删除过期键                                               |
| volatile-ttl    | 根据键值对象的 ttl 属性，删除最近将要过期数据，如果没有 ，回退到 noeviction 策略 |

> 优先使用 allkeys-lru 策略：业务数据中有明显的冷热数据区分，建议使用 allkeys-lru 策略
>
> 业务应用访问频率相差不大，没有明显的冷热数据区分，建议使用 allkeys-random 策略
>
> 业务中有置顶的需求，比如置顶视频、新闻，可以使用 volatile-lru 策略

## 持久化

### RDB 持久化

#### 概览

将内存中的数据生成快照保存到磁盘里面，保存的文件后缀是 <font color ='red'>.rdb</font>

rdb 文件是一个经过压缩的二进制文件，当 Redis 重新启动时，可以读取 rdb 快照文件恢复数据

其中，包括 rdbSave 和 rdbLoad 两个函数

* rdbSave 用于生成 RDB 文件并保存到磁盘
* rdbLoad 用于将 RDB 文件中的数据加载到内存中

<font color='Salmon'>RDB 文件是一个单文件的全量数据，适合数据的容灾备份与恢复</font>

* 通过 RDB 文件恢复数据库耗时较短，通常 1G 的快照文件加载到内存只需要 20s 左右

#### RDB 文件生成方式

1. 手动触发快照生成，通过 <font color ='red'>SAVE 和 BGSAVE</font> 命令

* <font color='red'>SAVE</font> 是一个同步式的命令，ta 会阻塞 Redis 服务器进程，直到 RDB 文件创建完成为止
  * 在服务器阻塞期间，服务器不能处理任何其他的命令请求
* <font color ='red'>BGSAVE</font> 是一个异步式的命令，会派生一个子进程，由子进程负责创建 RDB 文件，服务器进程（父进程）继续处理客户的命令
  * <font color='red'>基本过程</font>：
    * 客户端发起 BGSAVE 命令，Redis 主进程判断当前是否存在正在执行备份的子进程，如果存在则直接返回
    * <font color ='red'>父进程 fork 一个子进程</font>（fork 的过程中会造成阻塞的情况）
    * fork 创建的子进程开始根据父进程的内存数据生成临时的快照文件，然后替换源文件
    * 子进程备份完毕后会向父进程发送完成信息

2. <font color ='red'>自动触发保存</font>
   通过 save 选项设置多个保存条件，只要其中任意一个条件被满足，服务器就会执行 BGSAVE 命令
   只要满足以下 3 个条件中的任意一个，BGSAVE 命令就会被自动执行：
   * 服务器在 900s 之内，对数据库进行了至少 1次 修改
   * 服务器在 300s 之内，对数据库进行了至少 10次 修改
   * 服务器在 60s 之内，对数据库进行了至少 10000次 修改

### AOF 持久化

#### 概览

<font color = 'red'>AOF  会把 Redis 服务器每次执行的写命令记录到一个日志文件中</font>，当服务器重启时，再次执行 AOF 文件中的命令来恢复数据

如果 Redis 服务器开启了 AOF 持久化，会优先使用 AOF 文件来还原数据库状态

只有在 AOF 的持久化功能处于关闭状态时，服务器才会使用 RDB 文件还原数据库状态

<font color = 'red'>AOF 优先级大于 RDB</font>

#### 执行流程

AOF 不需要设置任何触发条件，对 Redis 服务器的所有写命令都会自动记录到 AOF 文件中

AOF 文件的写入流程可以分为 3个 步骤：

1. 命令追加（append）：将 Redis 执行的写命令追加到 AOF 的缓存区 <font color ='red'>aof_buf</font>
2. 文件写入（write）和文件同步（fsync）：AOF 根据对应的策略将 aof_buf 的数据<font color = 'red'>同步到硬盘</font>
3. 文件重写（rewrite）：定期对 AOF 进行<font color='red'>重写</font>，从而实现对写命令的压缩

#### AOF 缓存区的文件同步策略

* appendfysnc always：每执行一次命令保存一次
  * 命令写入 aof_buf 缓存区后立即调用系统 fsync 函数同步到 AOF 文件，fsync 操作完成后线程返回，整个过程是阻塞的
* appendfysnc no：不保存
  * 命令写入 aof_buf 缓存区调用系统 write 操作，不对 AOF 文件做 fsync 同步
  * 同步由操作系统负责，通常同步周期为 30s
* appendfysnc everysec：每秒钟保存一次
  * 命令写入 aof_buf 缓存区后调用系统 write 操作，write 完成后线程立刻返回，fsync 同步文件操作由单独的进程每秒调用一次

| 文件同步策略 | write 阻塞 | fsync 阻塞 | 宕机时的数据丢失量                           |
| ------------ | ---------- | ---------- | -------------------------------------------- |
| always       | 阻塞       | 阻塞       | 最多只丢失一个命令的数据                     |
| no           | 阻塞       | 不阻塞     | 操作系统最后一次对 AOF 文爱你 fsync 后的数据 |
| everysec     | 阻塞       | 不阻塞     | 一般不超过 1s 的数据                         |

#### 文件重写

> 把对 AOF 文件中的写命令进行合并，压缩文件体积，同步到新的 AOF 文件中，然后使用新的 AOF 文件覆盖旧的 AOF 文件

<font color ='each'>触发机制</font>：

* <font color = 'red'>手动触发</font>：调用 bgrewriteaof 命令，执行与 bgsave 有些类似
* <font color ='red'>自动触发</font>：
  * 根据 auto-aof-rewrite-min-size 和 auto-aof-rewrite-percentage 配置项，以及 aof_current_size 和 aof_base_size 的状态确定触发时机
  * auto-aof-rewrite-min-size：执行 AOF 重写时，文件的最小体积，默认值为 64MB
  * auto-aof-rewrite-percentage：执行 AOF 时，当前 AOF 大小（aof_current_size）和上一次重写时 AOF 大小（aof_base_size） 的比值

<font color ='each'>重写流程</font>：

* 客户端通过 bgrewriteaof 命令对 Redis 主进程发起 AOF 重写请求
* 主进程通过 fork 操作创建子进程，这个过程主进程是阻塞的
* 主进程的 fork 操作完成后，继续处理其他命令，把新的命令同时追加到 aof_buf 和 aof_rewrite_buf 缓冲区中
  * 在文件重写完成之前，主进程会继续把命令追加到 aof_buf 缓冲区，这样可以避免 AOF 重写失败造成数据丢失，保证原有的 AOF 文件的正确性
  * 由于 fork 操作运用写时复制技术，子进程只能共享 fork 操作时的内存数据，主进程会把新命令追加到一个 aof_rewrite_buf 缓冲区中，避免 AOF 重写时丢失这部分数据
* 子进程读取 Redis 进程中的数据快照，生成写入命令并按照命令合并规则批量写入到新的 AOF 我呢间
* 子进程写完新的 AOF 的文件后，向主进程发信号（怎么进行的信号发送？？？？）
* 主进程接收到子进程的信号后，将 aof_rewrite_buf 缓冲区中的写命令追加到 AOF 文件
* 主进程使用新的 AOF 文件替换旧的 AOF 文件，AOF 重写过程完成

### RDB&AOF

#### RDB的优缺点

* 优点：
  * RDB 是一个压缩过的非常紧凑的文件，保存着某个时间点的数据集，适合做数据的备份、灾难恢复
  * 与 AOF 持久化相比，恢复大数据集会更快些

* 缺点：
  * 数据安全性不入 AOF，保存整个数据集是个重量级的过程，可能几分钟一次持久化，如果服务器宕机，可能丢失几分钟的数据
  * Redis 数据集较大时，fork 的子进程要完成快照会比较耗费 cpu 和时间

#### AOF 的优缺点

* 优点：
  * 数据更完整，安全性更高，秒级数据丢失
  * AOF 文件是一个只进行追加的命令文件，且写入操作是以 Redis 协议的格式保存，内容是可读的，适合误删紧急恢复
* 缺点：
  * 对于相同的数据集，AOF 文件的体积要远大于 RDB 文件，数据恢复也会比较慢

#### RDB&AOF 混合持久化

Redis 4.0 版本提供了一套基于 AOF-RDB 的混合持久化机制，保留了两种持久化机制的优点

然后，<font color='red'>重写的 AOF 文件由两部分组成，一部分是 RDB 格式的头数据，另一部分是 AOF 格式的尾部命令</font>

在 Redis 服务器启动的时候：

* 可以预先加载 AOF 文件头部全量的 RDB 数据
* 然后再重放 AOF 文件尾部增量的 AOF 命令，从而大大减少重启过程中数据还原的时间

## 基本原理

### redis 协议

RESP，是一种简单的文本协议，用于在客户端和服务器之间操作和传输数据

RESP 协议描述了不同类型数据结构，并且定义了请求和响应之间如何以这些数据结构进行交互

## 单线程模式

Redis 的网络 IO 和键值对读写是由一个线程来完成的

Redis 在处理客户端请求时包括获取（读）、解析、执行、内容返回（写）等都由一个顺序串行的主线程处理

由于 Redis 在处理命令的时候是单线程作业的，所以会有一个 Socket 队列

* 每一个到达 de 服务端命令来了之后不会立马被执行，而是进入队列，然后被线程的事件分发器逐个执行

![image-20241012180832932](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410121808034.png)

> Redis 的其他功能，比如持久化、异步删除、集群数据同步等，都是交由额外线程执行的

## 哨兵模式

### 概览

Redis 的主从复制模式下，一旦主节点由于故障不能提供服务，需要手动将从节点晋升为主节点，同时还需要通知客户端更新主节点地址

> Redis 2.8 以后提供了 Redis Sentinel 哨兵机制来解决这个问题
>
> （注册中心 心跳机制） 

### Redis Sentinel 的主要功能

Sentinel 是一个管理多个 Redis 实例的工具，ta 可以实现对 Redis 的监控、通知、自动故障转移

* 监控：Sentinel 会不断检查主服务器和从服务器是否正常运行
* 通知：当被监控的某一个 Redis 服务器出现问题，Sentinel 通过 API 脚本向管理员或其他的应用程序发送通知
* 自动故障转移：当主节点不能正常工作时，Sentinel 会开始一次自动的故障转移操作，ta 会将与失效主节点是主从关系的其中一个从节点升级为新的主节点，并且将其他的从节点指向新的主节点
* 配置提供者：在 Redis Sentinel 模式下，客户端应用在初始化时连接的是 Sentinel 节点集合，从中获取主节点的信息

### 主观下线和客观下线

默认情况下，每个 Sentinel 节点会以每秒一次的频率对 Redis 节点和其 ta 的 Sentinel 节点发送 PING 命令，并通过节点的回复来判断节点是否在线

#### 主观下线

* 适用于所有主节点和从节点
* 如果 down-after-millisenconds 毫秒内，Sentinel 没有收到目标节点的有效回复，则会判定该节点为主观下线

#### 客观下线

* 只适用于主节点
* 如果主节点出现故障，Sentinel 节点会通过 sentinel is-master-down-by-addr 命令，向其他 Sentinel 节点询问对该节点的状态判断
* 如果超过 quorum 个数的节点判定主节点不可达，则该 Sentinel 节点会判断主节点为客观下线

### 工作原理

1. 每个 Sentinel 以每秒钟一次的评率，向 ta 所知的主服务器、从服务器以及其 ta Sentinel 实例发送一个 PING 命令
2. 如果实例距离最后一次有效回复 PING 命令的时间超过 down-after-millisenconds 所指定的值，这个实例会被 Sentinel 标记为主观下线
3. 如果一个主服务器被标记为主观下线，并且有足够的 Sentinel 在指定的时间范围内同意这一判断，那么这个主服务器被标记为客观下线
4. Sentinel 和其 ta Sentinel 协商主节点的状态，如果主节点处于 SDOWN 状态，则投票自动选出新的主节点，将剩余的从节点指向新的主节点进行数据复制

![image-20241013134904978](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202410131349077.png)

### 脑裂问题

在 Redis 哨兵模式或集群模式中，由于网络原因，导致主节点（Master）与哨兵（Sentinel）和从节点（Slave）的通讯中断。此时，哨兵就会误以为主节点已宕机，就会 在从节点中选举出一个新的主节点，此时 Redis 的集群中就会出现了两个主节点的问题。

#### **脑裂问题影响**

<font color='red'>Redis 脑裂问题会导致数据丢失</font>

当旧的 Master 变为 Slave 之后 de 执行流程如下：

* Slave（旧Master）会向 Master（新）申请全量数据
* Master 会通过 Bgsave 的 方式生成当前 RDB 快照，并且将 RDB 发送给 Slave
* Slave 拿到 RDB 之后，先进行 Flush 清空当前数据（此时第四步旧客户端给 ta 的发送的数据就丢失了）
* 之后再加载 RDB 数据，初始化自己当前的数据

在执行到第三步时，原客户端在旧 Master 写入的数据就丢失了

#### 解决脑裂问题

Redis 提供了一下两个配置，通过一下两个配置可以尽可能的避免脑裂导致数据丢失的问题：

* <font color='red'>min-slaves-to-write</font>：与主节点通信的从节点数量必须大于等于该值主节点，否知主节点拒绝写入
* <font color='red'>min-slaves-max-lag</font>：主节点与从节点通信 de ACK 消息延迟必须小于该值，否则主节点拒绝写入

这两个配置项必须同时满足，不然主节点拒绝写入

## 集群

### 概览

Redis 3.0 之前，使用哨兵（Sentinel）机制来监控各个节点之间的状态

在 3.0 版本正式推出，解决了 Redis 在分布式方面的需求

#### 数据分区

Redis Cluster 采用虚拟槽分区，所有的键根据哈希函数映射到 0~16383 整数槽内

* 计算公式：slot = CRC16（KEY） & 16383
* 每个节点负责维护一部分槽以及槽所映射的键值数据

#### 为什么 Redis 集群的最大槽数是 16384 个

2^14 = 16384、 2^16 = 65536

* 如果槽位是 65536 个，发送心跳信息的消息头是 65536 / 8 / 1024 = 8k
* 如果槽位是 16384 个，发送心跳信息的消息头是 16384 / 8 / 1024 = 2k

因为 Redis 每秒都会发送一定数据量的心跳包，如果消息头是 8k，有些太大了，浪费网络资源

Redis 的集群主节点数量一般不会超过 1000 个 

* 集群中节点越多，心跳包的消息体内的数据就越多，如果节点过多，也会造成网络拥堵

so，Redis Cluster 的节点建议不超过 1000 个，对于节点数在 1000 个以内的 Redis Cluster，16384 个槽位完全够用

#### 集群的功能限制 

* key 批量操作支持有限：类似 mset、mget 操作，目前支持对具有相同 slot 值 key 执行批量操作；对于映射为不同 slot 值的 key 由于执行 mset、mget等操作可能存在于多个节点上，因此不被支持
* key 事务操作支持有限：只支持多 key 在同一节点上的事务操作，当多个 key 分布在不同的节点上时，无法使用事务功能；单机下 Redis 可以支持 16个数据库（db0~db15），集群模式下只能使用一个数据库空间，即 db0







