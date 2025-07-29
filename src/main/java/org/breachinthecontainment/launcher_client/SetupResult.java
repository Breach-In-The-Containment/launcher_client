// SetupResult.java

package org.breachinthecontainment.launcher_client;

/**
 * Enum to represent the outcome of the first-time setup process.
 */
public enum SetupResult {
    SUCCESS,
    FAILURE,
    MISCOUNT_ERROR // New state for when file count doesn't match tree.txt
}
