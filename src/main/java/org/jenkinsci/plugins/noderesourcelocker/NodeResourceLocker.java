package org.jenkinsci.plugins.noderesourcelocker;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.PrintStream;

@SuppressWarnings("unused")
public class NodeResourceLocker extends BuildWrapper {

    // Default to 15 minutes
    private static final int DEFAULT_TIMEOUT_SECONDS = 15 * 60;

    private static final String DEFAULT_RESOURCE_NAME = "node";

    private final String resourceName;

    private int timeoutSeconds;

    @DataBoundConstructor
    public NodeResourceLocker(String resourceName) {
        String name = Util.fixEmptyAndTrim(resourceName);
        if (name == null) {
            name = DEFAULT_RESOURCE_NAME;
        }
        this.resourceName = name;
    }

    public String getResourceName() {
        return resourceName;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @DataBoundSetter
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        // Check for custom timeout
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }

        // Try to take the named lock, which may time out
        takeResourceLock(launcher, resourceName);

        // Taking the lock succeeded; allow the build to proceed
        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                // Remove the lock file
                listener.getLogger().println(String.format("Giving up resource '%s'...", resourceName));
                return getResourceLockFile(launcher, resourceName).delete();
            }
        };
    }

    private void takeResourceLock(Launcher launcher, String name) throws InterruptedException, IOException {
        // TODO: Check for existence of lock file up front. If it's older than timeout * 4 -- assume it can be deleted?

        // Get the filename we want to grab
        final FilePath file = getResourceLockFile(launcher, name);

        // Keep trying for the configured amount of time
        final long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);

        // Loop (and sleep) until the lock file no longer exists, allowing us to claim it
        long sleepMs = 5 * 1000;

        final PrintStream logger = launcher.getListener().getLogger();
        logger.println(String.format("Attempting to lock resource '%s'...", name));
        while (System.currentTimeMillis() < deadline) {
            // Try and grab the resource lock file
            if (!file.exists()) {
                logger.println("Got resource lock!");
                file.touch(System.currentTimeMillis());
                return;
            }

            // Sleep for a bit
            Thread.sleep(Math.min(sleepMs, 15 * 1000));

            // Back off a wee bit
            sleepMs *= 1.2;
        }

        // Timed out
        throw new AbortException("Timed out trying to obtain lock...");
    }

    private static FilePath getResourceLockFile(Launcher launcher, String name) {
        return new FilePath(launcher.getChannel(), String.format("/tmp/jenkins-%s.lock", name));
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        public String getDisplayName() {
            return "Take resource lock";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

    }

}

