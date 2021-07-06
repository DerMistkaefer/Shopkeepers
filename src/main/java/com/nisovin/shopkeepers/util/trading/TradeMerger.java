package com.nisovin.shopkeepers.util.trading;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.nisovin.shopkeepers.api.events.ShopkeeperTradeEvent;
import com.nisovin.shopkeepers.util.Ticks;
import com.nisovin.shopkeepers.util.Validate;

/**
 * Merges sequentially triggered shopkeeper trades that involve the same player, shopkeeper, and items.
 * <p>
 * Once a trade is encountered that cannot be merged with the previous trades, or once a certain maximum duration has
 * passed, or the {@link TradeMerger} is {@link #onDisable() disabled}, an initially provided {@link Consumer} is
 * informed about the merged trades so that they can be further processed.
 */
public class TradeMerger {

	/**
	 * Different trade merging behaviors.
	 * <p>
	 * Regardless of the chosen {@link MergeMode}, trades are always only merged if they take place sequentially and
	 * involve the same player, shopkeeper, and items.
	 */
	public enum MergeMode {
		/**
		 * Merges equivalent trades that were triggered by the same {@link InventoryClickEvent} (eg. when multiple
		 * trades are automatically triggered by a single shift click).
		 */
		SAME_CLICK_EVENT,
		/**
		 * Merges equivalent trades over a certain duration. By default, the maximum time span between successive merged
		 * trades is 5 seconds, and the maximum time span between the first and the last merged trade is 15 seconds.
		 */
		DURATION
	}

	private static final long DEFAULT_MERGE_DURATION_TICKS = 300L; // 15 seconds
	private static final long DEFAULT_NEXT_MERGE_TIMEOUT_TICKS = 100L; // 5 seconds
	// In order to avoid restarting the next merge timeout task too often (i.e. for performance reasons), the actual
	// next merge timeout duration may dynamically vary by this amount. Since the primary purpose of this task is to
	// detect the absence of trades that are manually triggered by players, it does not make much of a noticeable
	// difference whether the trade merging is aborted slightly earlier or later.
	private static final long NEXT_MERGE_TIMEOUT_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);

	private final Plugin plugin;
	private final Consumer<MergedTrades> mergedTradesConsumer;
	private final MergeMode mergeMode;
	// The maximum time span between the first and the last merged trade:
	private long mergeDurationTicks; // Can be 0 to disable the trade merging
	private long mergeDurationNanos;
	// The maximum time span between successive merged trades:
	private long nextMergeTimeoutTicks;
	private long nextMergeTimeoutNanos;

	private MergedTrades previousTrades = null;
	// The expected maximum nano time by which this merge will end. If the server lags, the actual end time can be
	// later. This is used to estimate whether it makes sense to start or skip a new timeout task for the next merge.
	// TODO In order to account for server lag, check more frequently whether one of the trade merging timeouts has
	// already been reached?
	private long mergeEndNanos;
	private long lastMergedTradeNanos;

	private BukkitTask mergeDurationTask = null;
	private BukkitTask nextMergeTimeoutTask = null;
	// This is set to the lastMergedTradeNanos at the time the nextMergeTimeoutTask is started.
	private long nextMergeTimeoutStartNanos;

	public TradeMerger(Plugin plugin, MergeMode mergeMode, Consumer<MergedTrades> mergedTradesConsumer) {
		Validate.notNull(plugin, "plugin");
		Validate.notNull(mergeMode, "mergeMode");
		Validate.notNull(mergedTradesConsumer, "mergedTradesConsumer");
		this.plugin = plugin;
		this.mergedTradesConsumer = mergedTradesConsumer;
		this.mergeMode = mergeMode;
		if (mergeMode == MergeMode.SAME_CLICK_EVENT) {
			this.setMergeDurations(1L, 1L);
		} else {
			this.setMergeDurations(DEFAULT_MERGE_DURATION_TICKS, DEFAULT_NEXT_MERGE_TIMEOUT_TICKS);
		}
	}

	/**
	 * Sets the maximum merge duration and the next merge timeout durations in ticks.
	 * <p>
	 * Calling this method is only valid if this {@link TradeMerger} is not yet used to merge trades, and if the
	 * {@link MergeMode} of this {@link TradeMerger} is {@link MergeMode#DURATION}.
	 * 
	 * @param mergeDurationTicks
	 *            the maximum duration in ticks between the first and the last merged trade, not negative,
	 *            <code>0</code> to disable the trade merging
	 * @param nextMergeTimeoutTicks
	 *            the maximum duration in ticks between two successive merged trades, not negative, has no effect if
	 *            <code>0</code>, or if greater than or equal to {@code mergeDurationTicks}
	 * @return this {@link TradeMerger}
	 */
	public TradeMerger withMergeDurations(long mergeDurationTicks, long nextMergeTimeoutTicks) {
		Validate.State.isTrue(previousTrades == null, "This TradeMerger cannot be reconfigured while it is already merging trades.");
		Validate.State.isTrue(mergeMode == MergeMode.DURATION, "Calling this method is only valid when using MergeMode DURATION.");
		this.setMergeDurations(mergeDurationTicks, nextMergeTimeoutTicks);
		return this;
	}

	private void setMergeDurations(long mergeDurationTicks, long nextMergeTimeoutTicks) {
		Validate.isTrue(mergeDurationTicks >= 0, "mergeDurationTicks cannot be negative");
		Validate.isTrue(nextMergeTimeoutTicks >= 0, "nextMergeTimeoutTicks cannot be negative");
		this.mergeDurationTicks = mergeDurationTicks;
		this.mergeDurationNanos = Ticks.toNanos(mergeDurationTicks);
		this.nextMergeTimeoutTicks = nextMergeTimeoutTicks;
		this.nextMergeTimeoutNanos = Ticks.toNanos(nextMergeTimeoutTicks);
	}

	public void onEnable() {
	}

	public void onDisable() {
		// Process the previous trades, if there are any:
		// This also stops the delayed tasks.
		this.processPreviousTrades();
	}

	/**
	 * Tries to merge the given trade with the previous trades, and triggers the processing of the previous trades if
	 * they could not be merged.
	 * 
	 * @param tradeEvent
	 *            the trade event
	 */
	public void mergeTrade(ShopkeeperTradeEvent tradeEvent) {
		Validate.notNull(tradeEvent, "tradeEvent");
		// In order to check if the trade can be merged with the previous trades, we most likely need to retrieve item
		// copies from the event. By creating the new MergedTrades right away, instead of only afterwards when it is
		// actually required, we can cache these once retrieved item copies. This is therefore cheaper most of the time.
		MergedTrades newMergedTrades = new MergedTrades(tradeEvent);
		long nowNanos = System.nanoTime();
		if (previousTrades == null) {
			// There are no previous trades to merge with.
			previousTrades = newMergedTrades;
			mergeEndNanos = nowNanos + mergeDurationNanos;
			lastMergedTradeNanos = nowNanos;
			this.startDelayedTasks();
		} else if (this.tryMergeTrades(previousTrades, newMergedTrades, mergeMode)) {
			// The trade was merged with the previous trades.
			lastMergedTradeNanos = nowNanos;
		} else {
			// The trade could not be merged with the previous trades.
			this.processPreviousTrades();
			assert previousTrades == null;
			previousTrades = newMergedTrades;
			mergeEndNanos = nowNanos + mergeDurationNanos;
			lastMergedTradeNanos = nowNanos;
			this.startDelayedTasks();
		}
	}

	private boolean canMergeTrades(MergedTrades target, MergedTrades other, MergeMode mergeMode) {
		if (mergeMode == MergeMode.SAME_CLICK_EVENT) {
			if (target.getInitialTrade().getClickEvent() != other.getInitialTrade().getClickEvent()) {
				return false;
			}
		}
		return target.canMerge(other);
	}

	private boolean tryMergeTrades(MergedTrades target, MergedTrades other, MergeMode mergeMode) {
		if (this.canMergeTrades(target, other, mergeMode)) {
			// Merge trades:
			target.addTrades(other.getTradeCount());
			return true;
		}
		return false;
	}

	private void endDelayedTasks() {
		this.endMergeDurationTask();
		this.endNextMergeTimeoutTask();
	}

	private void endMergeDurationTask() {
		if (mergeDurationTask != null) {
			mergeDurationTask.cancel();
			mergeDurationTask = null;
		}
	}

	private void endNextMergeTimeoutTask() {
		if (nextMergeTimeoutTask != null) {
			nextMergeTimeoutTask.cancel();
			nextMergeTimeoutTask = null;
		}
	}

	private void startDelayedTasks() {
		// End the previous tasks, if they are active:
		this.endDelayedTasks();

		// A merge duration of 0 effectively disables the trade merging:
		if (mergeDurationTicks == 0) {
			this.processPreviousTrades();
			return;
		}

		// Start the delayed tasks that end the trade merging after certain maximum durations:
		this.startMergeDurationTask();
		this.startNextMergeTimeoutTask();
	}

	private void startMergeDurationTask() {
		// End the previous task, if there is one:
		this.endMergeDurationTask();

		// Start a new delayed task that ends the trade merging after a certain maximum duration:
		mergeDurationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			assert previousTrades != null; // Otherwise this task would have already been cancelled
			mergeDurationTask = null;
			this.processPreviousTrades();
		}, mergeDurationTicks);
	}

	private void startNextMergeTimeoutTask() {
		// This timeout is not used if its duration is 0, or if its duration is greater than or equal to the merge
		// duration. This also excludes the cases where the trade merging is disabled (i.e. when the merge duration is
		// 0), or where the merge mode is SAME_CLICK_EVENT (i.e. when the merge duration is 1).
		if (nextMergeTimeoutTicks == 0 || nextMergeTimeoutTicks >= mergeDurationTicks) return;
		assert mergeMode == MergeMode.DURATION;

		// End the previous task, if there is one:
		this.endNextMergeTimeoutTask();

		// In order to avoid restarting this task very frequently (eg. after every merged trade), we instead only start
		// this task once and then check afterwards whether there have been any trades in the meantime. If there has
		// been another trade, a new next merge timeout task is started with a delay according to the remaining timeout
		// duration for the last merged trade.
		long nowNanos = System.nanoTime();
		long nanosSinceLastMergedTrade = nowNanos - lastMergedTradeNanos;
		assert nanosSinceLastMergedTrade >= 0;
		// If the server lagged, this may end up being negative:
		long remainingTimeoutNanos = nextMergeTimeoutNanos - nanosSinceLastMergedTrade;

		// We avoid starting a new task if the remaining timeout duration is below a certain threshold. This threshold
		// also captures the case where the remainingTimeoutNanos is less than 0, or the resulting task delay ticks is
		// less than 1.
		if (remainingTimeoutNanos <= NEXT_MERGE_TIMEOUT_THRESHOLD_NANOS) {
			this.processPreviousTrades();
			return;
		}

		// We avoid starting a new task if the trade merging is expected to end earlier, or roughly at the same time (by
		// up to NEXT_MERGE_TIMEOUT_THRESHOLD_NANOS) as the task anyways:
		if (mergeEndNanos <= nowNanos + remainingTimeoutNanos + NEXT_MERGE_TIMEOUT_THRESHOLD_NANOS) {
			return;
		}

		// Start a new delayed task that ends the trade merging after a certain maximum duration:
		long taskDelayTicks = Ticks.fromNanos(remainingTimeoutNanos);
		assert taskDelayTicks >= 1; // Due to the threshold checked above
		nextMergeTimeoutStartNanos = lastMergedTradeNanos; // Keep track of the timestamp of the last merged trade
		nextMergeTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			assert previousTrades != null; // Otherwise this task would have already been cancelled
			nextMergeTimeoutTask = null;

			// If there has been another trade in the meantime, restart this timeout task:
			if (lastMergedTradeNanos != nextMergeTimeoutStartNanos) {
				this.startNextMergeTimeoutTask();
			} else {
				// Otherwise, abort the trade merging:
				this.processPreviousTrades();
			}
		}, taskDelayTicks);
	}

	/**
	 * Stops merging trades with the previous trades that are still pending to be processed, and processes them.
	 * <p>
	 * Calling this method has no effect if there are no pending trades to process.
	 */
	public void processPreviousTrades() {
		if (previousTrades == null) return;
		this.endDelayedTasks();
		mergedTradesConsumer.accept(previousTrades);
		previousTrades = null;
	}
}
