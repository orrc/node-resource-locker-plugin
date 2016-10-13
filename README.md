# Node Resource Locker plugin for Jenkins

This is a naïve implementation of per-node resource locking for Jenkins freestyle jobs.

For a given build machine, this plugin ensures that the build steps for a given build will only execute if no other
build on the same machine, with the same resource name defined, is running.

i.e. By enabling the "Take resource lock" option, and providing a resource name (e.g. `ios-simulator`), concurrent
builds of this job, or builds of other jobs with the same resource name will not be allowed to execute their build steps
at the same time.

## Usage
Enable the "Take resource lock" build environment option, and provide a resource name.

Builds will then wait until the named lock is available before executing, checking every few seconds.

## Note
Once the lock has been released by one build, there is no guarantee as to which build will obtain the lock next,
i.e. it is not based on which build started first.

## Pipeline
This plugin has no Pipeline implementation, but is equivalent to the following:

```groovy
def withResource(String name, Closure body) {
    // Try to take the named lock, which may time out
    takeResourceLock(name)

    // If successful, execute the closure, releasing the lock afterwards
    try {
        body()
    } finally {
        releaseResourceLock(name)
    }
}

private takeResourceLock(String name) {
    // Block for up to 15 minutes
    timeout(15) {
        // Loop (and sleep) until the lock file no longer exists, allowing us to claim it
        def file = getResourceLockFile(name)
        waitUntil {
            def exitCode = sh script: "[ ! -f '${file}' ] && touch '${file}'", returnStatus: true
            return exitCode == 0
        }
    }
}

private releaseResourceLock(String name) {
    def file = getResourceLockFile(name)
    sh "rm -f -- '${file}'"
}

private static String getResourceLockFile(String name) {
    return "/tmp/jenkins-${name}.lock"
}
```