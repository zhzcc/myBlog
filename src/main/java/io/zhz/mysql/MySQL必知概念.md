## Delete、Drop 和 Truncate

* delete、truncate 仅仅删除表里面的数据，drop 会把表的结构也删除
* delete 是 DML 语句，操作完成后，可以回滚，truncate 和 drop 是 DDL 语句，删除之后立即生效，不能回滚
* 执行效率：drop > truncate > delete

## MyISAM 与 InnoDB

* InnoDB 支持事务，MyISAM 不支持
* InnoDB 支持外键，MyISAM 不支持
* InnoDB 是聚集索引，数据文件是和索引绑定一起的
* MyISAM 是非聚簇索引，索引和数据文件是分离的，索引保存的是数据的指针
* InnoDB 不保存表的具体行数，执行 select count(*) from table 时需要全表扫描
* MyISAM 用一个变量保存整个表的行数，执行上述语句时只需要读出改变量即可，速度很快
* InnoDB 支持表、行（默认）级锁，MyISAM 支持表级锁

## Join 语句

**left join、right join、inner join** 的区别：

> left join（左连接）：
>
> * 返回包括左表中的所有记录和右表中联结字段相等的记录
> * 左表是驱动表，右表是被驱动表
>
> right join（右连接）：
>
> * 返回包括右表中的所有记录和左表中联结字段相等的记录
> * 右表是驱动表，左表是被驱动表
>
> innner join（等值连接）：
>
> * 只返回两个表中联结字段相等的行
> * 数据量比较小的表作为驱动表，大表作为被驱动表

----

**join 查询在有索引条件下**：

> * 驱动表有索引不会使用到索引
> * 被驱动表建立索引会使用到索引、
>
> 所以在以小表驱动大表的情况下，给大表建立索引会大大提高查询效率

**Join 原理**：

> Simple Nested-Loop：
>
> * 驱动表中的每一条记录与被驱动表中的记录进行比较判断（笛卡尔积）
> * 对于两表联结来说，驱动表只会被访问一遍，但驱动表却要被访问到好多遍
>
> Index Nested-Loop：
>
> * 基于索引进行连接的算法
> * 他要求被动表驱动表上有索引，可以通过索引来加速查询
>
> Block Nested-Loop：
>
> * 它使用 Join Buffer 来减少内部循环读取表的次数
> * Join Buffer 用以缓存联接需要的列
>
> 选择 Join 算法优先级：
>
> * Index Nested-LoopJoin > Block Nested-Loop Join > Simple Nested-Loop Join
>
> 当不使用 Index Nested-Loop Join 的时候，默认使用 Block Nested-Loop Join

![image-20240922213139478](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409222131563.png)

## 分页查询优化

```sql
select * from table 
where 
type = 2 
and level = 9 
order by id asc 
limit 190289,10;
```

> 延迟关联：
>
> * 通过 where 条件提取出主键，再将该表与原数据表关联，通过主键 id 提取数据行，而不是通过原来的二级索引提取数据行
>
> * ```sql
>   select a.* from table a,
>   (
>       select id from table 
>       where type = 2 
>       and level =9 
>       order by id asc 
>       limit 190289,10
>   ) b 
>   where a.id = b.id;
>   ```
>
> 书签方式：
>
> * 找到 limit 第一个参数对应的主键值， 在根据这个主键值再去过滤并 limit
>
> * ```sql
>   select * from table 
>   where 
>   id > (
>       select * from table 
>       where type = 2 
>       and level = 9 
>       order by id asc 
>       limit 190289, 1
>   	) 
>   limit 10;
>   ```

## 事务

### ACID

* 原子性（atomicity）：指事务是一个不可分割的组织，要么全部提交，要么全部失败回滚
* 一致性（consistency）：指事务执行前后，数据从一个合法性状态变换到另外一个合法性状态（状态与业务相关）
* 隔离型（isolation）：指一个事务的执行不能被其他事务干扰；一个事务内部的操作 及使用的数据对并发的其他事务是隔离的，并发执行的各个事务之间不能互相干扰
* 持久性（durability）：指一个事务一旦被提交，ta 对数据库中数据的改变就是永久性的，其他操作和数据库故障并应该对其有任何影响

### 数据并发产生的问题

> 丢失更新（Lost Update）：
>
> * 两个事务，如 Session A、Session B，如果事务 Session A 修改了另一个未提交事务 Session B 修改过的数据，意味着发生了丢失更新
>
> 脏读（Dirty Read）：
>
> * 两个事务，如 Session A、Session B，Session A读取了已经被 Session B更新但还没有被提交的字段，如果后来 Session B 回滚，那么 Session A 读取的内容就是临时且无效的
>
> 不可重复读（Non-Repeatable Read）：
>
> * 两个事务，如 Session A、Session B，Session A 读取了一个字段，然后 Session B更新了该字段，如果后来 Session A 再次读取同一个字段，该字段的值发生了变化
>
> 幻读（Phantom）：
>
> * 两个事务 Session A、Session B，Session A 从一个表中读取了一个字段，然后 Session B在该表中插入了一些新的行，如果后来 Session A 再次读取同一个表，就会多出几行

### 事务隔离级别

> READ UNCOMMITTED（读未提交）：
>
> * 所有事务都可以看到其他未提交事务的执行结果， <font color =  'red'>**不能避免脏读、不可重复读、幻读**</font>
>
> READ COMMITTED（读已提交）：
>
> * 一个事务只能看见已经提交的事务所做的改变，<font color = 'red'>**可以避免脏读，但不可重复读、幻读问题仍然存在**</font>
>
> REPEATABLE READ（重复读）：
>
> * 事务 A 在读到一条数据后，此时事务 B 对该数据进行了修改并提交，事务 A 在读该数据，读取到的还是原来的内容，<font color = 'red'>**可以避免脏读、不可重复读，但幻读问题仍然存在，且是 MySQL 默认隔离级别**</font>
>
> SERIALIZABLE（可串行化）：
>
> * 确保事务可以从一个表中读取相同的行

| 隔离级别                | 丢失更新 | 脏读 | 不可重复读 | 幻读 |
| ----------------------- | -------- | ---- | ---------- | ---- |
| Read Uncommitted（RU）  | ×        | √    | √          | √    |
| Read Committed（RC）    | ×        | ×    | √          | √    |
| Repeatable Read（默认） | ×        | ×    | ×          | √    |
| Serializable            | ×        | ×    | ×          | ×    |

### MySQL 默认隔离级别为什么是可重复读（RR）

MySQL 在 5.0 之前，binlog 只支持 STATEMENT 这中格式，而这种格式在读已提交（RC）这个隔离级别下主从复制是有 bug 的，因此 MySQL 将可重复读（RR）作为默认的隔离级别

**STATEMENT 主从复制的 bug**：

* 在 master 上执行的顺序为先删后插，而此时 binlog 为 STATEMENT 格式，ta 记录的顺序为先插后删
* 从（slave）同步的是 binlog，如果从机执行的顺序 和主机不一致，就会出现主从不一致

**隔离级别设为可重复读（RR），在该隔离级别下引入间隙锁**：

* 当 Session A 执行 delete 语句时，会锁住间隙
* 那么，Session B 执行插入语句就会阻塞住

### 为什么大家将隔离级别设为读已提交(RC)？

<font color = 'green'>在 RR 隔离级别下，存在间隙锁，导致出现死锁的几率比 RC 大的多</font>

<font color = 'green'>在 RR 隔离级别下，条件未命中索引会锁表，而在 RC 隔离级别下，只锁行</font>

<font color='red'>在 RC 隔离级别下，半一致性读（semi-consistent）特性增加了 update 操作的并发性</font>

> <font color='each'>半一致性读</font>：<font color='pink'>一个 update 语句，如果读到一行已经加锁的记录，此时 InnoDB 返回记录最近提交的版本，有MySQL 上层判断此版本是否满足 update 的 where 条件</font>
>
> * <font color='red'>若满足（需求更新），则 MySQL 会重新发作一次读操作，此时会读取行的最新版本（并加锁）</font>
>
> 如下 Session A，Session B代码：
>
> <font color='green'>Sesssion A 执行：</font>
>
> ```sql
> update test set color = 'blue' where color = 'red';
> ```
>
> <font color='green'>先不 Commit 事务；</font>
>
> 与此同时，<font color='pink'>Session B 执行：</font>
>
> ```sql
> update test set color = 'blue' where color = 'white';
> ```
>
> <font color = 'BrickRed'>Session B 尝试加锁的时候，发现已经存在锁，InnoDB 会开启 semi-consistent read，返回最新的 committed 版本（1,red），（2,white），（5,red）,（7,white）；</font>
>
> <font color='VioletRed'>MySQL 会重新发起一次读操作，此时会读取行的最新版本（并加锁）;</font>
>
> <font color='RedOrange'>而在 RR 隔离级别下， Session B 只能等待</font>
>
> <font color = 'Salmon'>互联网项目推荐使用：读已提交（Read Commited）隔离级别</font>

### 事务实现原理

> 原子性：使用 undo log（回滚日志），undo log 记录了回滚需要的信息
>
> * 当实物执行失败或调用了 rollback，导致事务需要回滚，便可以 undo log 中的信息将数据回滚到修改前的样子
>
> 隔离性：使用悲观锁和乐观锁对事务处理
>
> 持久性：使用 redo log （重写日志）
>
> 一致性：通过原子性、隔离性、持久性来保证一致性

## MVCC

MVCC 是为了解决 <font color='red'>读-写</font> 之间阻塞的问题（排它锁会阻塞读操作），写操作还是需要加锁（Next-Key Lock）；

如果没有 MVCC，那么修改数据的操作会加排它锁，其他的读写操作都会阻塞，这样的话效率会比较低。

<font color = 'red'>MVCC 通过 Undo Log + Read View 进行数据读取</font>

* Undo Log 保存了历史快照
* Read View 规则判断当前版本的数据是否可见

### 快照读与当前读

快照读：

> 快照读读取的是快照数据：
>
> * 不加锁的简单的 SELECT 都属于快照读，即不加锁的非阻塞读
>
> 快照读的实现是基于 MVCC，ta 在很多情况下，避免了加锁操作，降低了开销：
>
> * 既然是基于多版本，那么快照读读取到的<font color='Rhodamine'>并不一定是数据的最新版本，而有可能是之前的历史版本</font>
>
> 快照读的前提是隔离级别不是串行级别，串行级别下的快照读会退化成当前读
>
> ```sql
> select * from goods where ...
> ```

当前读：

> 读取的是记录的最新版本，读取时还要保证其他并发事务不能修改当前记录，会对读取的记录进行加锁
>
> <font color = 'Bittersweet'>加锁的 SELECT ,或者对数据进行增删改都会进行当前读</font>
>
> ```sql
> select * from goods lock in share mode; # 共享锁
> select * from goods for update： # 排它锁
> insert into goods values ... # 排它锁
> delete from goods where ... # 排它锁
> update goods set ... where ... # 排它锁
> ```

### Undo Log 版本链

InnoDB 聚簇索引记录中包含 3 哥隐藏的列：

* <font color='red'>DB_ROW_ID</font>（隐藏的自增 ID）：
  如果表没有主键，InnoDB 会自动按 ROW ID 产生一个聚集索引树
* <font color='red'>DB_TRX_ID</font>（事务 ID）：
  记录最近更新这条记录的事务 ID，大小为 6 个字节
* <font color='red'>DB_ROLL_PTR</font>（回滚指针）：
  指向该行回滚段的指针，大小为 7 个字节;该行记录上所有旧版本，在 undo 中都通过链表的形式组织

![image-20240924192459097](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409241924132.png)

> 每次对该记录进行改动，都会记录一条 undo 日志，每条 undo 日志都已一个 <font color = 'red'>roll_pointer</font> 属性
>
> 会将这些 undo 日志都连起来，串成一个链表，这个链表被称为版本链
>
> * 版本链的头节点，就是当前记录最新的值
> * 每个版本中包含生成该版本时对应的 事务 id
>
> ![image-20240924192847299](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409241928334.png)

### ReadView

ReadView 就是事务在使用 MVCC 机制进行快照读操作时产生的读视图；

<font color='Salmon'>判断版本链中的哪个版本是当前事务可见的，这是 ReadView 要解决的主要问题</font>

**ReadView 主要包含 4 个内容**：

* <font color='red'>creator_trx_id</font>：创建这个 Read View 的事务 ID
* <font color='red'>trx_ids</font>：在生成 ReadView 时当前系统中活跃的读写事务的事务 id 列表（活跃：启动了但还没提交）
* <font color='red'>up_limit_id</font>：活跃的事务最小的事务 ID
* <font color='red'>low_limit_id</font>：表示生成 ReadView 时系统中应该分配给下一个事务的 id 值

**ReadView 规则**：

* 若被访问版本 <font color='red'>trx_id</font> 属性值与 ReadView 中的 <font color='red'>creator_trx_id</font> 值相同：
  则当前事务在访问 ta 自己修改的记录，所以该版本 可以 被当前事务访问
* 若被访问版本 <font color='red'>trx_id</font> 属性值 小于 ReadView 中的 <font color='red'>up_limit_id</font> 值：
  则生成该版本的事务与当前事务生成 ReadView 前已经提交，所以该版本 可以 被当前事务访问
* 若被访问版本的 <font color='red'>trx_id</font> 属性值 大于或等于 ReadView 中的 <font color='red'>low_limit_id</font> 值：
  则生成该版本的事务在当前事务生成 ReadView 后才开启，所以该版本 不可以 被当前事务访问
* 若访问版本的 <font color='red'>trx_id</font> 属性值在 ReadView 的 <font color='red'>up_limit_id</font> 和 <font color='red'>low_limited</font> 之间，
  需要判断一下 <font color='red'>trx_id</font> 属性值是不是在 <font color='red'>trx_ids</font> 列表：
  * 若在，则创建 ReadView 时生成该版本的事务还是活跃的，该版本不可以被访问
  * 若不在，说明创建 ReadView 时生成该版本的事务已经被提交，该版本可以被访问

### MVCC整体操作流程

首先 获取事务自己的版本号，也就是事务 ID；

获取 ReadView ；

查询到的数据，然后与 ReadView 中的事务版本号进行对比；

如果不符合 ReadView 规则，就需要从 Undo Log 中获取历史快照；

最后 返回不符合规则的数据；

如果某个版本的数据对当前事务不可见的话，那就顺着版本链找到下一个版本的数据，继续按照上边的步骤判断可见性，知道版本链中的最后一个版本；

如果最后一个版本也不可见的话，那么就意味着该条记录对该事务不完全不可见，查询结果就不包含该记录；

<font color = 'Salmon'>READ COMMITTED 事务，在每次查询开始时都会生成一个独立的 ReadView；</font>

<font color = 'Salmon'>REPEATABLE READ 事务，只会在第一次执行查询语句时生成一个 ReadView，之后的查询就不会重复生成了；</font>

### MVCC 幻读被彻底解决了嘛

可重复读隔离级别（默认隔离级别），根据不同的查询方式，分别提出了避免幻读的方案：

* 针对快照读（<font color='red'>普通 select 语句</font>），是通过 <font color='red'>MVCC</font> 方式解决了幻读
* 针对当前读（<font color='red'>select ... for update 等语句</font>），<font color='red'>是通过 next-key lock（记录锁 + 间隙锁）</font>方式解决幻读

## 索引

索引是帮助 MySQL **高效获取数据**的**数据结构（有序）**

### 索引分类

<font color='each'>**单列索引**</font>：基于单个字段建立的索引

* 唯一索引：之索引中的索引节点值不允许重复，一般配合唯一约束使用
* 主键索引：一种特殊的唯一索引，和普通索引的区别在于不允许有空值
* 普通索引：通过 <font color='red'>KEY、INDEX</font> 关键字创建的索引

<font color='each'>**多列索引**</font>：有多个字段组合建立的索引

<font color='each'>**聚簇索引和非聚簇索引**</font>：

* 聚簇索引（主键索引）：叶子节点存放的是实际数据，所有完成的用户数据都存放在聚簇索引的叶子节点
* 非聚簇索引（二级索引）：叶子节点存放的是主键值，而不是实际数据

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409242045089.png" alt="image-20240924204506935"  />

<font color='each'>**二级索引**</font>：

使用二级索引字段作为查询条件时，如果要查询的数据都在聚簇索引的叶子节点里，需要检索两颗 B+ 树：

* 先在二级索引的 B+ 树找到对应的叶子节点，获取主键值
* 再用上一步获取的主键值，再聚簇索引中的 B+ 树检索对应的叶子节点，然后获取要查询的数据

这个过程也叫做 <font color='Salmon'>**回表查询**</font>

<font color='each'>**全文索引**</font>：

类似于 ES、Solr 搜索中间件中的分词器；只能创建在 CHAR、VARCHAR、TEXT 等这些文本类型字段上，而且使用全文索引查询时，条件字符数量必须大于 3 才生效

### 适合索引创建

* where 条件语句查询的字段
* 关联字段需要建立索引
* 排序字段
* 分组字段
* 统计字段，如 count()、max()

### 不适合索引创建

* 频繁更新的字段
* 报数据比较少的
* 数据重复且发布比较均匀的字段
* 参与列计算的

### 索引覆盖

要查询的列，在使用的索引中已包含；结合 索引下推

### 索引结构

<font color='red'>InnoDB</font> 中使用了 <font color='VioletRed'>B+ </font>树 来实现索引

* 所谓的索引就是一颗 B+ 树，一个表有多少个索引就有多少颗 B+ 树

<font color='red'>InnoDB</font> 的整数字段建立索引为例：

* 页数据：

* 一个页默认 <font color='red'>16kb</font>，整数（<font color='red'>bigint</font>）子弹的长度为 <font color='red'>8b</font>，另外还跟着 <font color='red'>6b</font> 的指向其子树的指针，这意味着一个索引页可以存储接近 <font color='red'>1200</font> 条数据（<font color='red'>16kb/14b ≈ 1170</font>）
* 如果这颗 <font color='VioletRed'>B+ </font> 树 高度为 4，就可以存 1200 的 3 次方的值，差不多 17 亿条数据
* 页数据中怎么取行数据：

考虑到树根节点总是在内存中的，树的第二层大概率也在内存中，所以一次搜索最多只访问 2 次磁盘 IO

> 假设 1 亿 数据量的表，根据主键 <font color='red'>id </font> 建立了 <font color='VioletRed'>B+ </font> 树索引，搜索 <font color='red'>id = 2699</font> 的数据，流程如下：
>
> * 内存中直接获取树跟索引页，对树根索引页内的目录进行二分查找，定位到第二层的索引页
> * 内存中直接获取第二层的索引页，对索引内的目录进行二分查找，定位到第三层的索引页
> * 从磁盘加载第三层的索引页到内存中，对索引页内的目录进行二分查找，定位到第四层数据页
> * 从磁盘加载第四层的数据页到内存中，数据页变成缓存页，对缓存页的目录进行二分查找，定位到具体的行数据

![image-20240924211009376](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409242110520.png)

### B+ 树

![image-20240924211018319](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409242110362.png)

<font color='BrickRed'>**B+ 树 与 B 树差一点**</font>：

* 叶子节点（最底部的节点）才会存放实际数据（索引+记录），非叶子节点只会放索引
* 所有索引都不会在叶子节点出现，叶子节点之间构成一个有序链表
  * <font color='Salmon'>InnoDB 的 <font color='VioletRed'>B+ </font> 树的叶子节点之间是用 双向链表 进行连接，既能向右遍历，也可向左遍历</font>
* 非叶子节点的索引也会同时存在在子节点中，并且是在子节点中所有索引的最大（或最小）
* 非叶子节点中有多少个子节点，就有多少个索引

B+ 树的非叶子节点不存放实际的数据，仅存放索引

* 因此数据量相同的情况下，相比存储即存索引又存记录的 <font color='VioletRed'>B </font> 树，<font color='VioletRed'>B+ </font> 树的非叶子节点可以存放更多的索引

因此 <font color='VioletRed'>B+ </font> 树 可以比 B树更 矮胖，查询底层节点的磁盘 I/O 次数更少

MyISAM 存储引擎：

* <font color='VioletRed'>B+ </font> 树 索引的叶子节点保存数据的物理地址，即用户数据的指针

InnoDB 存储引擎：

* <font color='VioletRed'>B+ </font> 树索引的叶子节点保存数据本身

### 为什么使用 B+ 树而不使用跳表？

<font color='VioletRed'>B+ </font> 树 是多叉树结构，每个节点都是一个 16k 的数据页，能存放较多索引信息

* 三层左右可以存储 <font color='red'>2kw</font> 左右数据，查询一次数据，如果这些数据页都在磁盘里，最多需要查询 <font color='each'>三次磁盘 IO</font>

跳表 是链表结构，一条数据一个节点，如果最底层要存放 <font color='red'>2kw</font> 数据，且每次查询都要能达到  <font color='each'>二分查找</font>的效果，2kw 大概在 2的24次方 左右，所以，跳表大概高度在  <font color='each'>24 层</font>左右

* 最坏情况下，这 24 层数据会分散在不同的数据页里，也即查一次数据会经历 <font color='each'>24 次磁盘 IO</font>

针对写操作，B+ 树 需要拆分合并索引数据页，跳表则独立插入，并且根据随机函数确定层数，没有旋转和维持平衡的开销，因此  <font color='each'>跳表 的写入性能会比 B+ 树 要好</font>

### 为什么 Redis 有序集合底层选择跳表而非 B+树

Redis 是基于内存的数据库，因此不需要考虑磁盘IO

* B+ 树 写入时，存在拆分和合并数据页的开销，目的是为了保持树的平衡
* 跳表在数据写入时，只需要通过随机函数生成当前节点的层数即可，然后更新一层索引，往其中加入一个节点，相比于 B+ 树而言，少了旋转平衡带来的开销

由于跳表的查询复杂度在 O(logn)，因此 Redis 中 ZSet 数据类型底层结合使用 skiplist 和 hash，用空间换时间，利用跳表支持范围查询和有序查询，利用 Hash 支持精确查询

### 为什么建议使用自增 ID 为主键

MySQL 在底层以数据页为单位来存储数据的，一个数据页大小默认为 16k，也就是说如果一个数据页存满了，MySQL 就会去申请个新的数据页来存储数据。

* 如果主键为自增 ID 的话，MySQL 在写满一个数据页的时候，直接申请另一个新数据页接着写就可以了。
* 如果主键是非自增 ID，为了确保索引有序，MySQL 就需要将每次插入的数据都放到合适的位置上。

当往一个快满或已满的数据页中插入数据时，新插入的数据会将数据页写满，MySQL 就需要申请新的数据页，并且把上个数据页中的部分数据挪到新的数据页上。
这就造成了<font color='red'>**页分裂**</font>，这个大量移动数据的过程是会严重影响插入效率的。

### 索引下推ICP

Index Condition Pushdown

> 一张 <font color='red'>student</font> 表，有<font color='red'> age_name</font> 的联合索引
>
> 执行查询 <font color='red'>explain select * from user where age > 10 and name = 'a'</font>;
>
> * 看见 <font color='red'>Extra</font> 中显示了 <font color='red'>Using index condition</font>，表示出现了 索引下推
>
> ICP 流程：
>
> * 联合索引首先通过条件索引找到 <font color='red'>age > 10</font> 的数据，根据联合索引中已经存在的 <font color='red'>name</font> 数据进行过滤，找到符合条件的数据

### 索引合并

MySQL 5.1之后进行引入，它可以在多个索引上进行查询，并将结果合并返回

### 索引失效

1、不满足最左索引匹配原则

2、索引上有计算

```sql
explain select * from table where id+1=2;
```

3、索引列用了函数

```sql
explain select * from table where SUBSTR(height,1,2)=17;
```

4、字段类型不同

5、like左边包含%

6、列对比

* id 字段本身是有主键索引的，同时height字段也建了普通索引的，并且两个字段都是int类型，类型是一样的
* 如果把两个单独建了索引的列，用来做列对比时索引就会失效

```sql
explain select * from table where id=height
```

7、使用 or 关键字

因为最后加的 address 字段没有加索引，从而导致其他字段的索引都失效了

如果使用了 or 关键字，那么 ta 前面和后面的字段都要加索引，不然所有的索引都会失效

```sql
explain select student  from table where id = 1 or height = '176' or address = 'beijing'
```

8、not int 和 not exists

主键字段使用 not in 关键字查询数据范围，可以走索引

而普通索引字段使用 not in 关键字查询数据范围。索引会失效

9、order by 没加 where 或 limit

如果 order by 语句中没有加 where 或 limit 关键字，该 sql 语句将不会走索引

```sql
explain select * from student order by code,name;
```

10、对不同的索引做 order by

```sql
explain select * from student order by code, heignt limit 1000;
```

### Explain

**table 列**：表示 explain 的一行正在访问那个表；

* 当 from 子语句中有子查询时，table 列是 <derivenN> 格式：
  表示当前查询依赖 id = N 的查询，先执行 id = N 的查询

* 当有 union 时， UNION RESULT 的 table 列的值为 <union1,2>，1 和 2 表示参与 union 的 select 行 id

**id 列**：表示 select 的序列号，有几个 select 就有几个 id，并且 id 的顺讯是按 select 出现的顺序增长的

* id 列越大执行优先级越高，id 相同则从上往下执行，id 为 NULL 最后执行

<font color='each'>**select_type 列**</font>：

* <font color='red'>SIMPLE</font>：查询语句中不包含 UNION 或者子查询的查询都算作 SIMPLE 类型
* <font color='red'>PRIMARY</font>：对于包含 UNION、UNION ALL 或者子查询的大查询来说，ta 是有几个小查询组成；
  其中最左边的查询 select_type 值就是 PRIMARY

<font color='each'>**type 列**</font>：从最优到最差分别为：<font color='red'>system > const > eq_ref > ref > range > index > ALL</font>

> 一般来说，得到保证查询达到 rang 级别，最好达到 ref

* <font color='red'>eq_ref</font>：primary key 或 unique key 索引使用，最多只会返回一条符合条件的记录
* <font color='red'>ref</font>：当通过普通二级索引来查询某个表，那么对该表的访问方法就可能是 ref；
  相比 eq_ref，不使用唯一索引，可能会找到多个符合条件的行
* <font color='red'>ref_or_null</font>：当对普通二级索引查询，该索引列的值可以是 NULL 值时，那么对该表的访问方法就可能是 ref_or_null
* <font color='red'>range</font>：范围扫描通常出现在 in()，between，>，<，>= 等操作中；
  使用一个索引来检索给定范围的行
* <font color='red'>index</font>：当使用索引覆盖，但需要扫描全部的索引记录时，该表的访问方法就是 index
* <font color='red'>index_merge</font>：一般情况下对于某个表的查询只能使用到一个索引，但在某些场景下可以使用多种索引合并的方式进行执行查询
* <font color='red'>ALL</font>：全表扫描

**possible_keys 列**：

> 显示查询可能使用那些索引来查找

key_len 列：

显示了索引里使用的字节数，通过这个值可以算出具体使用了索引中的那些列；

key len计算规则如下：

字符串：char(n)：n字节，varchar(n)：2字节，如果是utf-8，则长度 3n +2

数值类型：tinyint：1字节，smallint：2字节，int：4字节，bigint：8字节
时间类型：date：3字节，timestamp：4字节，datetime：8字节

**rows 列**：读取并检测的行数，注意这个不是结果集里的行数

* 如果使用全表扫描查询时，执行计划的 rows 列就代表预计需要扫描的行数
* 如果使用索引查询时，执行计划的 rows 列就代表预计扫描的索引记录行数

**ref 列**：显示了在 key 列记录的索引中，表查找值所用到的列或常量，常见的有：const（常量），字段名

* ref 列展示的就是与索引列做等值匹配的值是什么，比如只有一个常数或某个列

<font color='each'>**Extra 列**</font>：

* <font color='red'>Using index</font>：查询的列被索引覆盖，并且 where 筛选条件是索引的前导列；
  一般是使用了覆盖索引（索引包含了所有查询的字段）
* <font color='red'>Using where</font>：使用全表扫描来执行对某个表的查询，并且该语句的 where 子句中有针对该表的搜索条件时
* <font color='red'>Using where Using index</font>：查询的列被索引覆盖，并且 where 筛选条件是索引列之一，但不是索引的前导列
  * 意味着无法直接通过索引查找来查询符合条件的数据
* <font color='red'>Using index condition</font>：查询的列不完全被索引覆盖，where 条件中是一个前导列的范围
* <font color='red'>Using temporary</font>：在执行 DISTINCT、GROUP BY、UNION 等子句的查询时，如果不能利用索引来完成查询，MySQL 通过建立内部的临时表来执行查询
* <font color='red'>Using filesort</font>：会对结果使用一个外部索引排序，而不是按索引次序从表里读取行

## 日志文件

MySQL 日志主要包括错误日志、查询日志、慢查询日志、事务日志、二进制日志

### binlog

<font color='red'>binlog</font> 用于记录数据库执行的写入性操作（不包括查询）信息，以二进制的形式保存在磁盘中

* <font color='Rhodamine'><font color='red'>binlog</font> 是 <font color='red'>mysql</font> 的逻辑日志，并且由 <font color='red'>Server</font> 层进行记录，使用任何存储引擎的 <font color='red'>mysql</font> 数据库都会记录 <font color='red'>binlog</font> 日志</font>
* 逻辑日志：<font color='Rhodamine'>可以理解为记录的是 sql 语句</font>
* 物理日志：<font color='Rhodamine'>因为 <font color='red'> mysql </font>数据最终是保存在数据页中的，物理日志记录的就是数据页变更</font>

<font color='red'>binlog</font> 是通过追加的方式进行写入的，通过 <font color='red'>max_binlog_size</font> 参数设置每个 <font color='red'>binlog</font> 文件的大小，当文件大小达到给定值之后，会生成新的文件来保存日志

<font color='each'>binlog 使用场景</font>：

> 主从复制和数据恢复

<font color='each'>binlog 刷盘时机</font>：

> 对于 <font color='red'>InnoDB</font> 存储引擎而言，只有在事务提交时才会记录 <font color='red'>binlog</font>，此时记录还在内存中
>
> <font color='red'>那么binlog 什么时候刷到磁盘中？</font>
>
> * <font color='red'>mysql</font> 通过 <font color='red'>sync_binlog</font> 参数控制 <font color='red'>binlog 的刷盘时机，取值范围是 <font color='red'>0-N：</font></font>
>   * <font color='Salmon'>0：不去强制要求，有系统自行判断何时写入磁盘</font>
>   * <font color='Salmon'>1：每次 commit 的时候都要将 binlog 写入磁盘</font>
>   * <font color='Salmon'>N：每 N 个事务，才会将 binlog 写入磁盘</font>

<font color='each'>binlog 日志格式</font>：

* STATMENT：基于 SQL 语句的复制，每一条会修改数据的 sql 语句会记录到 <font color='red'>binlog</font> 中
  * 优点：不需要记录每一行的变化，减少 <font color='red'>binlog</font> 日志量，节约了 IO，从而提高了性能 
  * 缺点：在某些情况下会导致主从数据不一致，比如执行 <font color='red'>sysdate()、slepp()</font>等
* ROW：基于行的复制，记录哪条数据被修改了
  * 缺点：会产生大量的日志，<font color='red'>alter table</font> 的时候会让日志暴涨
* MIXED：基于 STATMENT 和 ROW 两种模式的混合复制
  * 一般的复制使用 STATEMENT 模式保存 <font color='red'>binlog</font>，对于 STATEMENT 模式无法复制的操作使用 ROW 模式保存 <font color='red'>binlog</font>

> 在 MySQL 5.7.7 之前，默认的格式是 STATEMENT，之后默认值是 ROW

### redo log

redo log <font color='Salmon'>记录事务对数据页做了那些修改</font>

<font color='Salmon'>redo log 包括两部分</font>：

* 内存中的日志缓冲（<font color='red'>redo log buffer</font>）
* 磁盘上的日志文件（<font color='red'>redo log file</font>）

<font color='red'>mysql</font> 没执行一条 <font color='red'>DML</font> 语句，先将记录写入 <font color='red'>redo log buffer</font>，后续某个时间节点再一次性将多个操作记录写到 <font color='red'>redo log file</font>，这种 先写日志，再写磁盘 的技术就是 <font color='red'>WAL（Write-Ahead Logging）</font>技术

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409251420586.png" alt="image-20240925142057463" style="zoom:50%;" />

<font color='red'>mysql</font> 支持三种 <font color='red'>redo log buffer</font> 写入 <font color='red'>redo log file</font> 的时机，通过 <font color='red'>innodb_flush_log_at_trx_commit</font> 参数配置

| 参数值              | 含义                                                         |
| ------------------- | ------------------------------------------------------------ |
| 0（延迟写）         | 事务提交时不会将 <font color='red'>redo log buffer</font> 中日志写入到 <font color='red'>os buffer，而是每秒写入 <font color='red'>os buffer</font> 并调用 <font color='red'>fsync() </font>写入到 redo log file</font> 中，当系统崩溃，会丢失 1 秒 钟的数据 |
| 1（实时写，实时刷） | 事务每次提交都会将 <font color='red'>redo log buffer</font> 中的日志写入 <font color='red'>os buffer</font> 并调用 <font color='red'>fsync()</font> 刷到 <font color='red'>redo log file</font> 中，因为每次提交都写入磁盘，IO 的性能较差 |
| 2（实时写，延迟刷） | 每次提交都仅写入到 <font color='red'>os buffer</font>，然后是每秒调用 <font color='red'>fsync()</font> 将 <font color='red'>os buffer</font> 中的日志写入到 <font color='red'>redo log file</font> |

redo log 记录形式：

> <font color='red'>redo log</font> 采用了大小固定，循环写入的方式，当写到结尾时，会回到开头循环写日志

redo log 与 binlog 却别：

|          | redolog                                                      | binlog                                                       |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 文件大小 | <font color='red'>redo log</font> 的大小是固定的             | <font color='red'>binglog</font> 可以通过配置参数 <font color='red'>max_binlog_size</font> 设置每个 <font color='red'>binlog</font> 文件的大小 |
| 实现方式 | <font color='red'>redo log</font> 是 <font color='red'>InnoDB</font> 引擎层实现的，并不是所有引擎都有 | <font color='red'>binlog</font> 是 <font color='red'>Server</font> 层实现的，所有引擎都可以使用 <font color='red'>binlog</font> 日志 |
| 记录方式 | <font color='red'>redo log</font> 采用 循环写的方式记录，当写到结尾时，会回到开头循环写日志 | <font color='red'>binlog</font> 通过追加的方式记录，当文件大小大于给定值后，后续的日志会记录到新的文件上 |
| 适用场景 | <font color='red'>redo log</font> 适用于崩溃恢复（<font color='red'>crash-safe</font>） | <font color='red'>binlog</font> 适用于主从复制和数据恢复     |

### undo log

<font color='red'>undo log</font> 主要记录了数据的逻辑变化

> 比如：一条 <font color='red'>INSERT</font> 语句，对应一条 <font color='red'>DELETE</font> 的 <font color='red'>undo log</font>；
> 对于每个 <font color='red'>UPDATE</font> 语句，对应一条相反的 <font color='red'>UPDATE</font> 的 <font color='red'>undo log</font>，这样在发生错误时，就能回滚到事务之前的数据状态

## 锁机制

### 锁结构

当一个事务想对这个条记录做改动时，会先查看内存是否有与该条记录关联的锁结构；

* 若发现没有时，则会在内存中生成一个锁结构与其关联

<font color='each'>锁结构</font> 内容：

* <font color='red'>trx</font>：记录锁结构由那个事务生成的
* <font color='red'>is_waiting</font>：当前事务是否在等待

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409261344464.png" alt="image-20240926134404348" style="zoom:50%;" />

### 锁分类

<font color='each'>读锁、写锁</font>：

* 读锁：<font color='each'>共享锁，用 S 表示</font>
  * <font color='Salmon'>针对同一份数据，多个事务的读操作可以同时进行而不会互相影响，相互不阻塞</font>

* 写锁：<font color='each'>排它锁，用 X 表示</font>

  * <font color='Salmon'>当前写操作没有操作完成前，ta 会阻断其他写锁和读锁</font>
  * <font color='Salmon'>这样就能确保在给定时间内，只有一个事务能执行写入，并防止其他用户读取正在写入的同一资源</font>

  对读取的记录加 S 锁：

  ```sql
  select ... lock in share mode;
  # 或
  select ... from share; # (8.0 新增语法) 
  ```

  对赌取得记录加 X 锁：

  ```sql
  select ... from update;
  ```

<font color='each'>表锁</font>：

* <font color='red'>LOCK TABLES t READ</font>：对表 t 加表级别的 <font color='red'>S 锁</font>
* <font color='red'>LOCK TABLES t WRITE</font>：对表 t 加表级别的 <font color='red'>X锁</font>

<font color='each'>意向锁</font>：

有两个事务,分别是 T1 和 T2，其中 T2 试图在该表级别上应用共享锁或排它锁：

* 如果没有意向锁，T2 就需要去检查各个页或行是否存在锁
* 如果存在意向锁，那么此时就会受到有 T1 控制的表级别意向锁的阻塞

如果事务想要获得数据表中某些记录的共享锁，需要在数据表上添加 <font color='red'>意向共享锁</font>

如果事务想要获得数据表中某些记录的排他锁，需要在数据表上添加 <font color='red'>意向排他锁</font>

* 意向锁会告诉其他事务已经有人锁定了表中的某些记录

在为数据行加共享/排他锁之前，InnoDB 会先获取该数据行所在数据表的对应意向锁

<font color='each'>元数据锁MDL 锁）</font>：

MDL的作用是 保证读写的正确性

* 如果一个查询正在遍历一个表中的数据，而执行期间另一个线程对这个表结构做变更，增加了一列，那么查询线程拿到的结果跟表结构对不上

当对一个表做增删改查操作的时候，加 MDL 读锁

当要对表结构变更操作的时候，加 MDL 写锁

<font color='each'>行锁</font>：

行锁（Row Lock）也被称为记录锁，就是锁住某一行（某条记录 Row）

<font color='each'>间隙锁（Gap Locks）</font>：

MsSQL 解决幻读问题方案有两种：

* 使用 MVCC  
* 采用加锁（Gap Locks）

图中 id 值为 8 的记录加了 gap 锁，意味着 <font color='red'>不允许别的事务在 id 值为 8 的记录前边的间隙插入新纪录</font>

* id 列的值（3,8）这个区间的新纪录是不允许立即插入的

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262017186.png" alt="image-20240926201734128" style="zoom:50%;" />

<font color='each'>临建锁（Next-Key Lock）</font>：

既想锁住某条记录，又想阻止其他事务在该记录前边的间隙插入新纪录，InnoDB 就提出了 Next-Key Locks

* ta 是在 可重复读 的情况下使用的数据库锁，InnoDB 默认的锁就是 <font color='red'>Next-Key Locks</font>

----------------------------------

1、update 命令会施加一个 X 型记录锁，X 型记录锁是谢谢互斥的

* 如果 A 事务对 goods 表中 id = 1的记录行加了记录锁，B 事务想要对这行记录加记录锁就会被阻塞

2、insert 命令会施加一个插入意向锁，但插入意向锁是互相兼容的

* 如果 A 事务 向 goods 表 inser 一条记录，不会影响 B 事务 insert 一条记录

### select ... for update 锁表还是锁行

* 若查询条件用了 索引/主键，<font color='red'>select ... for update</font> 会进行 行锁
* 如果是普通字段（无索引/主键），<font color='red'>select ... for update</font> 会进行 锁表

### 如何有效的避免死锁的发生

<font color='each'>设置事务等待所得超时时间</font>：

* 当一个事务的等待时间超过该值后，就对这个事务进行回滚，于是锁就释放了，另一个事务就可以继续执行了
* 在 InnoDB 中，参数 <font color='red'>innodb_lock_wait_timeout</font> 是用来设置超时时间的，默认值是 50 秒

<font color='each'>开启主动死锁机制</font>：

* 主动死锁检测在发生死锁后，主动回滚死锁连中的某一个事务，让其他事务得以继续执行
* 在 InnoDB 中，参数 <font color='red'>innodb_deadlock_detect</font> 设置为 on，表示开启这个逻辑，默认就是开启的

<font color='each'>修改数据库隔离级别为 RC</font>：

* MySQL 默认级别为 RR，RC 没有间隙锁 <font color='red'>Gap Lock</font> 和组合锁 <font color='red'>Next-Key Lock</font>，能一定程度的避免死锁的发生

尽量少使用当前读 <font color='red'>for update</font>，数据更新尽量使用主键

### 乐观锁

乐观锁假设认为数据一般情况下不会造成冲突，所以在数据进行提交更新的时候，才会正式对数据的冲突与否进行检测；如果发现冲突了，则让返回用户错误的信息，让用户决定如何去做

使用版本号实现乐观锁，版本号的实现方式有两种：<font color='each'>数据版本机制和时间戳机制</font>

* 数据版本（Version）记录机制实现：
  * 为数据增加一个版本标识，一般是通过为数据库表增加一个数据类型的 <font color='red'>version</font> 字段来实现的
  * 当读取数据时，将 version 字段的值一同读取，数据没更新一次，对此 version 值加 1
  * 当提交更新的时候，判断数据库表对应记录的当前版本信息与第一次取出来的 version 值进行对比
  * 若数据库表当前版本号与第一次取出来的 version 值相等，则予以更新，否则认为是过期数据

> <font color='Salmon'>这种版本号的方法并不适用于所有的乐观锁场景</font>：
>
> 当电商抢购活动时，大量并发进入，如果仅仅使用版本号或时间戳，就会出现大量用户查询出库存存在，但是却在扣减库存时失败了，而这个时候库存是确实存在的
>
> <img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262042025.png" alt="image-20240926204255968" style="zoom:50%;" />
>
> ```sql
> update goods 
> set status = 2, version = version +1
> where id = #{id} and version = #{version}
> ```
>
> <font color='Salmon'>使用条件限制实现乐观锁</font>：
>
> 这个适用于做数据安全校验，适合库存模型，扣分额和回滚份额，性能更高
>
> 更新库存操作如下：
>
> * <font color='red'>注意</font>：乐观锁的更新操作，最好用主键或者唯一索引来更新，这个样是行锁，否则更新时会锁表
>
> ```sql
> update goods
> set num = num - #{buyNum}
> where 
> id = #{id}
> and num - #{buyNum} >= 0
> and status = 1
> ```

## 基本原理

### 服务器处理客户端请求

无论客户端进程和服务器进程是采用哪种方式进行通信，最后实现的效果都是：<font color='Salmon'>客户端进程向服务器进程发送一段文本（MySQL语句），服务器进程处理后再向客户端进程发送一段文本（处理结果）</font>

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262114684.png" alt="image-20240926211411618" style="zoom:50%;" />

<font color='each'>连接管理</font>：

> 每当有一个客户端进程连接到服务器进程时，服务器进程都会创建一个线程来专门处理与这个客户端的交互。
>
> 当该客户端退出时会与服务器断开连接，服务器并不会立即把与该客户端交互的线程销毁掉，而是把 ta 缓存起来。
>
> 在另一个新的客户端再进行连接时，把这个缓存的线程分配给该新客户端。
>
> 这个样就起到了不频繁创建和销毁线程的效果，从而节省开销。
>
> <font color='red'>MySQL</font> 服务器会为每一个连接进来的客户端分配一个线程，但是线程分配的太多会严重影响系统性能。<font color='green'>所以也需要限制一下可以同时连接到服务器的客户端数量</font>。

<font color='each'>查询缓存</font>：

> <font color='red'>MySQL</font> 服务器会把刚刚处理过的查询请求和结果 <font color='red'>缓存</font> 起来，如果下次有一摸一样的的请求过来，直接从缓存中查找结果就好了。
>
> 这个查询缓存可以在不同客户端之间共享，也就是说如果客户端 A 刚刚查询了一个语句，而客户端 B 之后发送了同样的查询请求，那么客户端 B 的这次查询就可以直接使用查询缓存中的数据。
>
> 如果两个查询请求在任何字符上的不同，如：空格、注释、大小写，都会导致缓存不会命中。
>
> 如果查询请求中包含某些系统函数、用户自定义变量和函数、一些系统表，如 <font color='red'>mysql、information_schema、performance_schema</font> 数据库中的表，那这个请求就不会被缓存。
>
> MySQL 的缓存系统会监测涉及到的每张表，只要该表的结构或者数据被修改，如：对该表使用了 <font color='red'>insert、update、delete、truncate table、alter table、drop table</font> 或 <font color='red'>drop database</font> 语句，那使用该表的所有高速缓存查询都将变为无效并且从高速缓存中删除。
>
> > 注意：从MySQL 5.7.20 开始，不推荐使用查询缓存，并在 MySQL 8.0 中删除

<font color='each'>存储引擎</font>：

> 各种不同的存储引擎向 <font color='red'>MySQL server</font> 层提供统一的调用接口（也就是存储引擎 API），包含了几十个底层函数。
>
> 在 <font color='red'>MySQL server</font> 完成了查询优化后，只需按照生成的执行计划调用底层存储引擎提供的 API，获取到数据后返回给客户端就好了。

### Buffer Pool：

> <font color='red'>Buffer Pool</font> 作为缓存页：
>
> * <font color='red'>MySQL</font> 数据<font color='red'>以页为单位</font>，每页默认 <font color='red'>16kb</font>，称为数据页，在 <font color='red'>Buffer Pool</font> 里面会划分出<font color='red'>若干个缓存页</font>与数据对应
>
> 缓存页的元数据信息（描述数据）：
>
> * ta 与缓存页一一对应，包含一些所属表空间、数据页的编号、<font color='red'>Buffer Pool 中的地址</font>等
>
> 后续对数据的增删改查都在 <font color='red'>Buffer Pool</font> 里操作：
>
> * 查询：从磁盘加载到缓存，后续直接查缓存
> * 插入：直接写入缓存
> * 更新删除：缓存中存在直接更新，不存在加载数据页到缓存更新

<font color='each'>缓存页哈希表</font>：

> 如何在 <font color='red'>Buffer Pool</font> 里快速定位到对应的缓存页？
>
> * 使用哈希表来缓存 ta 们间的映射关系，时间复杂度是 <font color='red'>O(1)</font>
> * <font color='red'>表空间号 + 数据页号</font>，作为 <font color='red'>key</font>，缓存页的地址作为 <font color='red'>value</font>
> * 每次加载数据页到空闲缓存页时，就写入一条映射关系到<font color='red'>缓存页哈希表</font>中
>
> 后续的查询，就可以通过缓存页哈希表路由定位了

<font color='each'>Free 链表</font>：

> 使用链表结构，把空闲缓存页的<font color='red'>描述数据</font>放入链表中，这个链表称之为 <font color='red'>free</font> 链表
>
> * 往<font color='red'>描述数据</font>与<font color='red'>缓存页</font>写入数据后，就将该描述数据移除 <font color='red'>free</font> 链表
>
> <img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262140788.png" alt="image-20240926214020683" style="zoom:50%;" />

<font color='each'>Flush 链表</font>：

> 如果修改了 Buffer Pool 中某个缓冲页的数据，那么 ta 就与磁盘上的页不一致了，这个样的缓冲也被称之为脏页。
>
> 创建一个存储脏页的链表，凡是被修改过的缓冲页对应的控制块都会作为节点加入到这个链表中。
>
> * <font color='Salmon'>该链表也被称为 Flush 链表</font>
>
> 后续异步线程都从 <font color='red'>flush</font> 链表刷缓存页，当 <font color='red'>Buffer Pool </font>内存不足时，也会优先刷 <font color='red'>Flush</font> 链表里的缓存页

<font color='each'>LRU 链表</font>：

>借鉴 <font color='red'>LRU</font> 算法思想，把最少使用的缓存页淘汰（命中率低），提供 <font color='red'>LRU</font> 链表出来
>
>* 当 <font color='red'>free</font> 链表为空的时候，直接淘汰 <font color='red'>LRU</font> 链表尾部缓存页即可

### LRU 不足

<font color='each'>磁盘预读</font>：

> 如果按照简单 LRU 的思路实现内存淘汰，可能会导致部分真正的热点数据被预读的数据淘汰掉，从而预读的数据又不一定被使用到，之后缓存命中率就不会下降

<font color='each'>全表扫描</font>：

> <font color='Salmon'>全表扫描的过程其实也会不断地把数据页加载到 Buffer Pool 中</font>。
>
> 比如数据库备份时，就会把缓存中原有的热点数据淘汰，最终降低缓存命中率。

<font color='each'>冷热数据分离设计</font>：

> 给 <font color='red'>LRU 链表做冷热数据分离设计</font>，把 <font color='red'>LRU</font> 链表按一定比例，分为冷热区域，热区域称为 <font color='red'>young</font> 区域，冷区域称为 <font color='red'>old</font> 区域。
>
> 数据页第一次加载进缓存的时候，是先放入冷数据区域的头部，如果 1 秒后再次访问缓存页，则会移动到热区域的头部。
>
> 这样就保证了<font color='red'>预读机制与全表扫描</font>加载的数据都在链表队尾

### ChangeBuffer

> ChangBuffer 是 InnoDB 缓存区的一种特殊的数据结构，当用户执行 SQL 对非唯一索引进行更改时，如果索引对应的数据页不在缓存中时，InnoDB 不会直接加载磁盘数据到缓存数据页中，而是缓存对这些更改操作
>
> * <font color='Salmon'>这些更改操作可能由插入、更新或删除操作（DML）触发</font>

> ChangeBuffer 用于存储 SQL 变更操作，比如 Insert/Update/Delete等 SQL 语句
>
> ChangeBuffer 中的每个变更操作都有其对应的数据页，并且该数据页未加载到缓存中
>
> 当 ChangeBuffer 中变更操作对应的数据页加载到缓存中，InnoDB 会把变更操作 Merge 到数据页上
>
> InnoDB 会定期加载 ChangeBuffer 中操作对应的数据页到缓存中，并 Merge 变更操作

![image-20240926220413280](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262204470.png)

<font color='each'>在什么场景下会触发 ChangeBuffer 的 Merge 操作</font>？

> 访问变更操作对应的数据页
>
> InnoDB 后台定期 Merge
>
> 数据库 BufferPool 空间不足
>
> 数据库正常关闭时
>
> RedoLog 写满时

<font color='each'>为什么 ChangeBuffer 只缓存非唯一索引数据</font>？

> 由于唯一索引需要进行唯一性校验，所以对唯一索引进行更新时必须将对应的数据页加载到缓存中进行校验，从而导致 ChangeBuffer 失效

<font color='each'>ChangeBuffer</font> 适用场景：

> 数据库大部分索引是非唯一索引
>
> 业务是写多读少，或者不是写后立即读取

<font color='each'>普通索引还是唯一索引</font>？

> 从索引修改角度来看：
>
> * 由于唯一索引无法使用 ChangeBuffer，对索引的修改会引起大量的磁盘 IO，影响数据库性能
>
> 如果不是业务中要求数据库对某个字段做唯一性检查，最好使用普通索引而不是唯一索引

### SQL 查询

一条查询语句的执行过程一般是经过连接器、分析器、优化器、执行器等阶段后，最终抵达存储引擎

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262211835.png" alt="image-20240926221159604" style="zoom:50%;" />

### SQL 更新

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262212636.png" alt="image-20240926221251402" style="zoom:50%;" />

## 存储引擎

### InnoDB 行格式

> InnoDB 提供了 4 中行格式，分别是 Compact、Redundant、Dynamic 和 Compressed 行格式

### COMPACT 行格式

> 一行数据被分为了两个部分，一部分是记录的额外信息，一部分是记录的真实数据

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262215603.png" alt="image-20240926221532513" style="zoom:50%;" />

### 行溢出

> 页的大小默认情况下 16k，也就是 16384 字节：
>
> * VARCHAR(M) 最多可以存储的远远不止 16384 字节，这样就出现了一个页存放不了一条记录的局面
>
> 在 Compact 和 Redundant 行格式中：
>
> * 对于占用字节数非常大的列，在记录的真实数据中只会存储一小部分数据（768 个字节），剩余的数据分散存储在其他的页
>
> 为了可以找到 ta 们，在记录的真实数据中会记录这些页的地址
>
> <img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262218933.png" alt="image-20240926221857745" style="zoom:50%;" />

## 主从复制

![image-20240926221936660](https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262219734.png)

<img src="https://zhzcc.oss-cn-hangzhou.aliyuncs.com/202409262220800.png" alt="image-20240926222007568" style="zoom:50%;" />

### 三个线程：

* <font color='red'>binlog dump</font> 线程：
  * 主库中有数据更新时，将更新的事件类型写入到主库的 binlog 文件中，并创建 log dump 线程通知 slave 有数据更新
  * 将此时的 binlog 名称和当前更新的位置同时传给 slave 的 I/O 线程I/O
* <font color='red'>I/O</font> 线程：
  * 该线程连接到 master，向 log dump 线程请求一份指定 binlog 文件位置的副本，并将请求回来的 binlog 存到本地的 relay log 中
* <font color='red'>SQL</font> 线程：
  * 该线程检测到 relay log 有更新后，会读取并在本地做 redo 操作，将发生在主库的事件在本地重新执行一遍，来保证主从数据同步

### 基本过程

* 主库写入数据并且生成 binloig 文件
* 从库服务其上的 IO 线程链接 Master 服务器，请求从执行 binlog 日志文件中的指定位置开始读取 binlog 至从库
* 主库接收到从库的 IO 线程请求后，会根据 Slave 的请求信息粉皮读取 binlog 文件然后返回给从库的 IO线程
* Slave 服务器的 IO 线程请求后，会根据 Slava 的请求信息分批读取 binlog 文件，然后返回给从库的 IO 线程
* Slave 服务器的 IO 线程获取到 Master 服务器上 IO 线程发送的日志内容、日志文件以及位置点后，会将 binlog 日志内容依次写到 Slave 端自身的 Relay Log（即中继日志）
* 从库服务器的 SQL 线程会实时监测到本地 Relay Log 中新增了日志内容，然后把 RelayLog 中的日志翻译成 SQL 并且按照顺序执行 SQL 来更新从库的数据

### 主从延时原因

主从延迟主要是出现在 relay log 回放这一步

当主库的 TPS 并发较高，产生的 DDL 数量超过从库一个 SQL 线程所承受的范围，那么延时就产生了

还有就是可能与从库的大型 query 语句产生了锁等待

### 主从延时解决方案

缩短主从同步时间：

* 提升从库机器配置，可以和主库一样，甚至更好。
* 避免大事务。
* 搞多个从库，即一主多从，分担从库查询压力。
* 优化网络宽带。
* 选择高版本 MySQL，支持主库 binlog 多线程复制。

从业务场景考虑：

* 使用缓存：
  * <font color='red'>在同步写数据库的同时，也把数据写到缓存，查询数据时，会先查询缓存</font>
  * <font color='red'>这种情况会带来 MySQL 和 Redis 数据一致性问题</font>。
* 查询主库:
  * <font color='red'>直接查询主库，这种情况会给主库太大压力，核心场景可以使用，比如订单支付</font>。

### GTID 复制

GTID：从 MySQL 5.6.5 开始新增了一种基于 GTID 的复制方式。

* <font color='red'>通过 GTID 保证了每个在主库上提交的事务在集群中有一个唯一的ID</font>。
* <font color='red'>这种方式强化了数据库的主备一致性，故障恢复以及容错能力</font>。

在原来基于二进制日志的复制中，从库需要告知主库要从哪个偏移量进行增量同步:

* 如果指定 错误会造成数据的遗漏，从而造成数据的不一致。

借助GTID，在发生主备切换的情况下，MSQL的其它从库可以自动在新主库上找到正确的复制位置， 这大大简化了复杂复制拓扑下集群的维护 ，也减少了人为设置复制位置发生误操作的风险。
另外，基于GTID的复制可以忽略已经执行过的事务，减少了数据发生不一致的风险

<font color='each'>GTID复制原理流程</font>:
Master进行数据更新时、在事务前产生GTID号、一起记录到binlog日志

Slave的I/O线程将变更的binlog数据，写入到本地中继日志relay_log

Slave的SQL线程从中继日志中获取GTID号，和本地的binlog对比查看是否有记录

* <font color='red'>有记录，说明该GTID事务已执行，Slave数据库会忽略</font>
* <font color='red'>如果没有记录，Slave数据库从 relay_1og中继日志 中获取数据，且执行该GTID的事务，记录到binlog中</font>

根据GTID号就可以知道事务最初是在哪个数据库上提交的

有了GTID号、可以方便主从复制的故障切换

<font color='each'>主从故障切换</font>:
同一个事务的GTID在所有节点上都是一致的，Slave机器根据GTID就可以知道数据的停止点在哪.能够自动的获取GTID值，让运维更加省心了。并目，MySQL还提供了参数<font color='red'>master_auto_position</font>，能够自动的获取 GTID 值，让运维更加省心了

<font color='each'>GTID优缺点</font>:

* 优点：
  * 根据GTID可以明确知道事务最开始是在哪个数据库提交的
  * GTID对于宕机切换，非常方便，明确数据起止点
* 缺点：
  开启了GTID的数据库，和未开启的数据库实例之间、是无法混用复制的
