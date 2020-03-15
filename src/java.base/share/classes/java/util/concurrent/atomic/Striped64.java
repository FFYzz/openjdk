/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongBinaryOperator;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 */

/**
 * 64bit 的值 double / Long
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    /*
     * This class maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field. The table size
     * is a power of two. Indexing uses masked per-thread hash codes.
     * Nearly all declarations in this class are package-private,
     * accessed directly by subclasses.
     *
     * Table entries are of class Cell; a variant of AtomicLong padded
     * (via @Contended) to reduce cache contention. Padding is
     * overkill for most Atomics because they are usually irregularly
     * scattered in memory and thus don't interfere much with each
     * other. But Atomic objects residing in arrays will tend to be
     * placed adjacent to each other, and so will most often share
     * cache lines (with a huge negative performance impact) without
     * this precaution.
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.  When there is no contention, all
     * updates are made to the base field.  Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS. Table slots remain empty (null) until they are
     * needed.
     *
     * A single spinlock ("cellsBusy") is used for initializing and
     * resizing the table, as well as populating slots with new Cells.
     * There is no need for a blocking lock; when the lock is not
     * available, threads try other slots (or the base).  During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     *
     * The Thread probe fields maintained via ThreadLocalRandom serve
     * as per-thread hash codes. We let them remain uninitialized as
     * zero (if they come in this way) until they contend at slot
     * 0. They are then initialized to values that typically do not
     * often conflict with others.  Contention and/or table collisions
     * are indicated by failed CASes when performing an update
     * operation. Upon a collision, if the table size is less than
     * the capacity, it is doubled in size unless some other thread
     * holds the lock. If a hashed slot is empty, and lock is
     * available, a new Cell is created. Otherwise, if the slot
     * exists, a CAS is tried.  Retries proceed by "double hashing",
     * using a secondary hash (Marsaglia XorShift) to try to find a
     * free slot.
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions. When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.  Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all. However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.  We do not try to detect or remove such cells,
     * under the assumption that for long-running instances, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     */

    /**
     * Padded variant of AtomicLong supporting only raw accesses plus CAS.
     * <p>
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     */
    @jdk.internal.vm.annotation.Contended
    static final class Cell {
        /**
         * 存储元素的值
         */
        volatile long value;

        /**
         * 构造函数
         *
         * @param x
         */
        Cell(long x) {
            value = x;
        }

        /**
         * VarHandle 机制实现 CAS
         *
         * @param cmp
         * @param val
         * @return
         */
        final boolean cas(long cmp, long val) {
            return VALUE.compareAndSet(this, cmp, val);
        }

        /**
         * 重置
         */
        final void reset() {
            VALUE.setVolatile(this, 0L);
        }

        final void reset(long identity) {
            VALUE.setVolatile(this, identity);
        }

        final long getAndSet(long val) {
            return (long) VALUE.getAndSet(this, val);
        }

        // VarHandle mechanics
        /**
         * VarHandle 实例
         */
        private static final VarHandle VALUE;

        /**
         * 初始化 VarHandle 实例
         */
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VALUE = l.findVarHandle(Cell.class, "value", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * Number of CPUS, to place bound on table size
     * <p>
     * CPU 个数
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     * <p>
     * 一般是 2 的 n 次幂个
     * cells数组，存储各个段的值
     */
    transient volatile Cell[] cells;

    /**
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     * <p>
     * 最初无竞争时使用的，也算一个特殊的段
     */
    transient volatile long base;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     * <p>
     * 在扩容或者增加 cell 的时候充当锁
     */
    transient volatile int cellsBusy;

    /**
     * Package-private default constructor.
     */
    Striped64() {
    }

    //  cas 操作

    /**
     * CASes the base field.
     * <p>
     * cas 更新当前的计数值 base
     */
    final boolean casBase(long cmp, long val) {
        return BASE.compareAndSet(this, cmp, val);
    }

    /**
     * 更新当前的计数值并返回旧值
     *
     * @param val
     * @return
     */
    final long getAndSetBase(long val) {
        return (long) BASE.getAndSet(this, val);
    }

    /**
     * 获取锁，如果成功设置为1，则表示获取到锁
     * <p>
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     */
    final boolean casCellsBusy() {
        return CELLSBUSY.compareAndSet(this, 0, 1);
    }

    /**
     * 或得到当前线程的探测值
     * 会返回当前线程的 threadLocalRandomProbe 变量的值
     * <p>
     * Returns the probe value for the current thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int getProbe() {
        return (int) THREAD_PROBE.get(Thread.currentThread());
    }

    /**
     * 计算当前线程的随机探测值并设置到当前线程中。
     * <p>
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        THREAD_PROBE.set(Thread.currentThread(), probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *
     * @param x              the value
     * @param fn             the update function, or null for add (this convention
     *                       avoids the need for an extra field or function in LongAdder).
     * @param wasUncontended false if CAS failed before call
     */
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {
        // 存储线程的probe值
        int h;
        // 如果getProbe() 方法返回 0 ，说明随机数未初始化
        if ((h = getProbe()) == 0) {
            // 强制初始化
            ThreadLocalRandom.current(); // force initialization
            // 重新获取probe值
            h = getProbe();
            // 都未初始化，肯定还不存在竞争激烈
            wasUncontended = true;
        }
        // 是否发生碰撞标识
        boolean collide = false;                // True if last slot nonempty
        done:
        for (; ; ) {
            Cell[] cs;
            Cell c;
            int n;
            long v;
            // cells 已经初始化过
            if ((cs = cells) != null && (n = cs.length) > 0) {
                // 当前线程所在的 Cell 未初始化
                if ((c = cs[(n - 1) & h]) == null) {
                    // 当前无其它线程在创建或扩容 cells ，也没有线程在创建 Cell
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        // 新建一个 Cell ，值为当前需要增加的值
                        Cell r = new Cell(x);   // Optimistically create
                        // 再次检测 cellsBusy，并尝试更新它为 1
                        // 相当于当前线程加锁
                        if (cellsBusy == 0 && casCellsBusy()) {
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                // 重新获取 cells ，并找到当前线程 hash 到 cells 数组中的位置
                                // 这里一定要重新获取 cells ，因为 cs 并不在锁定范围内
                                // 有可能已经扩容了，这里要重新获取
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    // 把上面新建的 Cell 放在 cells 的 j 位置处
                                    rs[j] = r;
                                    break done;
                                }
                            } finally {
                                // 相当于释放锁
                                cellsBusy = 0;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    // 标记当前未出现冲突
                    collide = false;
                    // 当前线程所在的 Cell 不为空，且更新失败了
                    // 这里简单地设为 true ，相当于简单地自旋一次
                    // 通过下面的语句修改线程的 probe 再重新尝试
                } else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                    // 再次尝试 CAS 更新当前线程所在 Cell 的值，如果成功了就返回
                else if (c.cas(v = c.value,
                        (fn == null) ? v + x : fn.applyAsLong(v, x)))
                    break;
                    // 如果 cells 数组的长度达到了 CPU 核心数，或者 cells 扩容了
                    // 设置 collide 为 false 并通过下面的语句修改线程的 probe 再重新尝试
                else if (n >= NCPU || cells != cs)
                    collide = false;            // At max size or stale
                    // 上上个 elseif 都更新失败了，且上个条件不成立，说明出现冲突了
                else if (!collide)
                    collide = true;
                    // 明确出现冲突了，尝试占有锁，并扩容
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        // 检查是否有其它线程已经扩容过了
                        if (cells == cs)        // Expand table unless stale
                            // 新数组为原数组的两倍，并拷贝
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        // 释放锁
                        cellsBusy = 0;
                    }
                    // 已解决冲突
                    collide = false;
                    // 使用扩容后的新数组重新尝试
                    continue;                   // Retry with expanded table
                }
                // 更新失败或者达到了 CPU 核心数，重新生成 probe ，并重试
                h = advanceProbe(h);
                // 未初始化过 cells 数组，尝试占有锁并初始化 cells 数组
            } else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    // 检测是否有其它线程初始化过
                    if (cells == cs) {
                        // 新建一个大小为 2 的Cell数组
                        Cell[] rs = new Cell[2];
                        // 找到当前线程 hash 到数组中的位置并创建其对应的 Cell
                        rs[h & 1] = new Cell(x);
                        // 赋值给 cells 数组
                        cells = rs;
                        // 初始化成功
                        break done;
                    }
                } finally {
                    // 释放锁
                    cellsBusy = 0;
                }
            }
            // 如果有其它线程在初始化cells数组中，就尝试更新base
            // 如果成功了就返回
            // Fall back on using base
            else if (casBase(v = base,
                    (fn == null) ? v + x : fn.applyAsLong(v, x)))
                break done;
        }
    }

    private static long apply(DoubleBinaryOperator fn, long v, double x) {
        double d = Double.longBitsToDouble(v);
        d = (fn == null) ? d + x : fn.applyAsDouble(d, x);
        return Double.doubleToRawLongBits(d);
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn,
                                boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        done:
        for (; ; ) {
            Cell[] cs;
            Cell c;
            int n;
            long v;
            if ((cs = cells) != null && (n = cs.length) > 0) {
                if ((c = cs[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    break done;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                } else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (c.cas(v = c.value, apply(fn, v, x)))
                    break;
                else if (n >= NCPU || cells != cs)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == cs)        // Expand table unless stale
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            } else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    if (cells == cs) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        break done;
                    }
                } finally {
                    cellsBusy = 0;
                }
            }
            // Fall back on using base
            else if (casBase(v = base, apply(fn, v, x)))
                break done;
        }
    }

    // VarHandle mechanics
    /**
     * VarHandle 实例
     */
    private static final VarHandle BASE;
    private static final VarHandle CELLSBUSY;
    private static final VarHandle THREAD_PROBE;

    /**
     * 初始化 VarHandle 实例
     */
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BASE = l.findVarHandle(Striped64.class,
                    "base", long.class);
            CELLSBUSY = l.findVarHandle(Striped64.class,
                    "cellsBusy", int.class);
            l = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<>() {
                        public MethodHandles.Lookup run() {
                            try {
                                return MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
                            } catch (ReflectiveOperationException e) {
                                throw new ExceptionInInitializerError(e);
                            }
                        }
                    });
            THREAD_PROBE = l.findVarHandle(Thread.class,
                    "threadLocalRandomProbe", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
