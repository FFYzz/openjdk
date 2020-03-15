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

import jdk.internal.misc.Unsafe;

import java.lang.invoke.VarHandle;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

/**
 * A {@code long} value that may be updated atomically.  See the
 * {@link VarHandle} specification for descriptions of the properties
 * of atomic accesses.
 * AtomicLong 的应用场景以及使用时的注意事项：
 * An {@code AtomicLong} is used in applications
 * such as atomically incremented sequence numbers, and cannot be used
 * as a replacement for a {@link java.lang.Long}.
 * However, this class
 * does extend {@code Number} to allow uniform access by tools and
 * utilities that deal with numerically-based classes.
 *
 * @author Doug Lea
 * @since 1.5
 */

/**
 * 这个类中的方法基本都委托给了 Unsafe 来操作，所以具体原理的分析留到 Unsafe 类。
 */
public class AtomicLong extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 1927816293512124184L;

    /**
     * Records whether the underlying JVM supports lockless
     * compareAndSet for longs. While the intrinsic compareAndSetLong
     * method works in either case, some constructions should be
     * handled at Java level to avoid locking user-visible locks.
     * <p>
     * 是否支持 CAS 的标识
     */
    static final boolean VM_SUPPORTS_LONG_CAS = VMSupportsCS8();

    /**
     * Returns whether underlying JVM supports lockless CompareAndSet
     * for longs. Called only once and cached in VM_SUPPORTS_LONG_CAS.
     * <p>
     * native 方法，用于确定该机器是否支持 CAS
     */
    private static native boolean VMSupportsCS8();

    /**
     * This class intended to be implemented using VarHandles, but there
     * are unresolved cyclic startup dependencies.
     * <p>
     * 获得 Unsafe 的实例
     */
    private static final jdk.internal.misc.Unsafe U = jdk.internal.misc.Unsafe.getUnsafe();
    /**
     * 记录 AtomicLong 类中 value 字段的偏移量
     */
    private static final long VALUE = U.objectFieldOffset(AtomicLong.class, "value");

    /**
     * 当前的值
     */
    private volatile long value;

    /**
     * Creates a new AtomicLong with the given initial value.
     * <p>
     * 带参构造方法
     *
     * @param initialValue the initial value
     */
    public AtomicLong(long initialValue) {
        value = initialValue;
    }

    /**
     * Creates a new AtomicLong with initial value {@code 0}.
     * <p>
     * 无参构造方法
     */
    public AtomicLong() {
    }

    /**
     * Returns the current value,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     * <p>
     * 取到当前的值
     *
     * @return the current value
     */
    public final long get() {
        return value;
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setVolatile}.
     * <p>
     * 设置当前的值为一个新值，委托给 U 来设置
     *
     * @param newValue the new value
     */
    public final void set(long newValue) {
        // See JDK-8180620: Clarify VarHandle mixed-access subtleties
        U.putLongVolatile(this, VALUE, newValue);
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setRelease}.
     * <p>
     * 设置当前的值为一个新值，委托给 U 来设置，是一个 lazy 方法
     * 表示修改之后不会马上被其他线程可见，但是最终会被其他线程可见，
     * 有点类似于不加 Volatile
     *
     * @param newValue the new value
     * @since 1.6
     */
    public final void lazySet(long newValue) {
        U.putLongRelease(this, VALUE, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} and returns the old value,
     * with memory effects as specified by {@link VarHandle#getAndSet}.
     * <p>
     * 设置当前的值为一个新值，委托给 U 来设置，并返回旧的值
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final long getAndSet(long newValue) {
        return U.getAndSetLong(this, VALUE, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by {@link VarHandle#compareAndSet}.
     * <p>
     * cas 函数，传入 expectedValue 与 newValue，将当前的值设成 newValue。
     * 委托给了 U 的 compareAndSetLong 方法
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(long expectedValue, long newValue) {
        return U.compareAndSetLong(this, VALUE, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by {@link VarHandle#weakCompareAndSetPlain}.
     * <p>
     * Deprecated 就不看啦
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @see #weakCompareAndSetPlain
     * @deprecated This method has plain memory effects but the method
     * name implies volatile memory effects (see methods such as
     * {@link #compareAndExchange} and {@link #compareAndSet}).  To avoid
     * confusion over plain or volatile memory effects it is recommended that
     * the method {@link #weakCompareAndSetPlain} be used instead.
     */
    @Deprecated(since = "9")
    public final boolean weakCompareAndSet(long expectedValue, long newValue) {
        return U.weakCompareAndSetLongPlain(this, VALUE, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by {@link VarHandle#weakCompareAndSetPlain}.
     * <p>
     * 类似于 compareAndSet 方法
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetPlain(long expectedValue, long newValue) {
        return U.weakCompareAndSetLongPlain(this, VALUE, expectedValue, newValue);
    }

    /**
     * Atomically increments the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     * <p>
     * 将当前的值 + 1，委托给 U 实现
     *
     * <p>Equivalent to {@code getAndAdd(1)}.
     *
     * @return the previous value
     */
    public final long getAndIncrement() {
        return U.getAndAddLong(this, VALUE, 1L);
    }

    /**
     * Atomically decrements the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     * <p>
     * 将当前值 - 1，委托给 U 实现
     *
     * <p>Equivalent to {@code getAndAdd(-1)}.
     *
     * @return the previous value
     */
    public final long getAndDecrement() {
        return U.getAndAddLong(this, VALUE, -1L);
    }

    /**
     * Atomically adds the given value to the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     * <p>
     * 将当前值 + delta，委托给 U 实现
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final long getAndAdd(long delta) {
        return U.getAndAddLong(this, VALUE, delta);
    }

    /**
     * Atomically increments the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     * <p>
     * 将当前值 + 1，并返回新值，也是委托给 U 实现
     *
     * <p>Equivalent to {@code addAndGet(1)}.
     *
     * @return the updated value
     */
    public final long incrementAndGet() {
        return U.getAndAddLong(this, VALUE, 1L) + 1L;
    }

    /**
     * Atomically decrements the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     * <p>
     * 将当前值 - 1，并返回新值，委托给 U 实现
     *
     * <p>Equivalent to {@code addAndGet(-1)}.
     *
     * @return the updated value
     */
    public final long decrementAndGet() {
        return U.getAndAddLong(this, VALUE, -1L) - 1L;
    }

    /**
     * Atomically adds the given value to the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     * <p>
     * 将当前值 + delta，并返回新值，也是委托给 U 实现
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final long addAndGet(long delta) {
        return U.getAndAddLong(this, VALUE, delta) + delta;
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the current value with the results of
     * applying the given function, returning the previous value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     * <p>
     * 将当前的值设置成 LongUnaryOperator 函数返回的值。并返回旧值。
     * 为了排除其他线程对值修改的影响，updateFunction 需要是一个可以多次重复执行不影响正常结果的函数
     *
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final long getAndUpdate(LongUnaryOperator updateFunction) {
        long prev = get(), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = updateFunction.applyAsLong(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the current value with the results of
     * applying the given function, returning the updated value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     * <p>
     * 与 getAndUpdate 类似，只是返回的是新值
     *
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final long updateAndGet(LongUnaryOperator updateFunction) {
        long prev = get(), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = updateFunction.applyAsLong(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the current value with the results of
     * applying the given function to the current and given values,
     * returning the previous value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function is
     * applied with the current value as its first argument, and the
     * given update as the second argument.
     * <p>
     * 根据定义的 accumulatorFunction 函数，传入 prev 和 x，计算得到结果值。
     * 将当前的值赋值为结果值，并返回旧值。
     *
     * @param x                   the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final long getAndAccumulate(long x,
                                       LongBinaryOperator accumulatorFunction) {
        long prev = get(), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = accumulatorFunction.applyAsLong(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Atomically updates (with memory effects as specified by {@link
     * VarHandle#compareAndSet}) the current value with the results of
     * applying the given function to the current and given values,
     * returning the updated value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function is
     * applied with the current value as its first argument, and the
     * given update as the second argument.
     * <p>
     * 与 getAndAccumulate 类似，返回新值
     *
     * @param x                   the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final long accumulateAndGet(long x,
                                       LongBinaryOperator accumulatorFunction) {
        long prev = get(), next = 0L;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = accumulatorFunction.applyAsLong(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Returns the String representation of the current value.
     *
     * @return the String representation of the current value
     */
    public String toString() {
        return Long.toString(get());
    }

    /**
     * Returns the current value of this {@code AtomicLong} as an {@code int}
     * after a narrowing primitive conversion,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     *
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public int intValue() {
        return (int) get();
    }

    /**
     * Returns the current value of this {@code AtomicLong} as a {@code long},
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     * Equivalent to {@link #get()}.
     */
    public long longValue() {
        return get();
    }

    /**
     * Returns the current value of this {@code AtomicLong} as a {@code float}
     * after a widening primitive conversion,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     *
     * @jls 5.1.2 Widening Primitive Conversion
     */
    public float floatValue() {
        return (float) get();
    }

    /**
     * Returns the current value of this {@code AtomicLong} as a {@code double}
     * after a widening primitive conversion,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     *
     * @jls 5.1.2 Widening Primitive Conversion
     */
    public double doubleValue() {
        return (double) get();
    }

    // jdk9

    /**
     * Returns the current value, with memory semantics of reading as if the
     * variable was declared non-{@code volatile}.
     * <p>
     * 得到结果的值，委托给 U 来实现
     *
     * @return the value
     * @since 9
     */
    public final long getPlain() {
        return U.getLong(this, VALUE);
    }

    /**
     * Sets the value to {@code newValue}, with memory semantics
     * of setting as if the variable was declared non-{@code volatile}
     * and non-{@code final}.
     * <p>
     * 设置当前的值，委托给 U 来实现，如果当前的值非声明为 volatile
     *
     * @param newValue the new value
     * @since 9
     */
    public final void setPlain(long newValue) {
        U.putLong(this, VALUE, newValue);
    }

    /**
     * Returns the current value,
     * with memory effects as specified by {@link VarHandle#getOpaque}.
     * <p>
     * 通过 VarHandle#getOpaque 获得当前的值
     *
     * @return the value
     * @since 9
     */
    public final long getOpaque() {
        return U.getLongOpaque(this, VALUE);
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setOpaque}.
     * <p>
     * 通过调用 VarHandle#setOpaque 设置当前的值
     *
     * @param newValue the new value
     * @since 9
     */
    public final void setOpaque(long newValue) {
        U.putLongOpaque(this, VALUE, newValue);
    }

    /**
     * Returns the current value,
     * with memory effects as specified by {@link VarHandle#getAcquire}.
     * <p>
     * 通过调用 VarHandle#getAcquire 设置当前的值
     *
     * @return the value
     * @since 9
     */
    public final long getAcquire() {
        return U.getLongAcquire(this, VALUE);
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setRelease}.
     * <p>
     * 通过 VarHandle#setRelease 设置当前的值
     *
     * @param newValue the new value
     * @since 9
     */
    public final void setRelease(long newValue) {
        U.putLongRelease(this, VALUE, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} if the current value,
     * referred to as the <em>witness value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchange}.
     * <p>
     * 通过 VarHandle#compareAndExchange 设置值
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final long compareAndExchange(long expectedValue, long newValue) {
        return U.compareAndExchangeLong(this, VALUE, expectedValue, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} if the current value,
     * referred to as the <em>witness value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchangeAcquire}.
     * <p>
     * 通过 VarHandle#compareAndExchangeAcquire 设置值
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final long compareAndExchangeAcquire(long expectedValue, long newValue) {
        return U.compareAndExchangeLongAcquire(this, VALUE, expectedValue, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} if the current value,
     * referred to as the <em>witness value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchangeRelease}.
     * <p>
     * 通过 VarHandle#compareAndExchangeRelease 设置值
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final long compareAndExchangeRelease(long expectedValue, long newValue) {
        return U.compareAndExchangeLongRelease(this, VALUE, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSet}.
     * <p>
     * 通过 VarHandle#weakCompareAndSet 设置值
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetVolatile(long expectedValue, long newValue) {
        return U.weakCompareAndSetLong(this, VALUE, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSetAcquire}.
     * <p>
     * 通过 VarHandle#weakCompareAndSetAcquire 设置值
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetAcquire(long expectedValue, long newValue) {
        return U.weakCompareAndSetLongAcquire(this, VALUE, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSetRelease}.
     * <p>
     * 通过 VarHandle#weakCompareAndSetRelease 设置值
     *
     * @param expectedValue the expected value
     * @param newValue      the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetRelease(long expectedValue, long newValue) {
        return U.weakCompareAndSetLongRelease(this, VALUE, expectedValue, newValue);
    }

}
