# Lawnchair Prebuilt JARs

Launcher3 has some dependencies on internal AOSP modules. 
To build Lawnchair, you have to build AOSP and obtain these JARs.

## Usage

Lawnchair relies on these JARs:

| File                       | Command                 |
|----------------------------|-------------------------|
| SystemUI-statsd-15.jar     | `m SystemUI-statsd`     |
| WindowManager-Shell-15.jar | `m WindowManager-Shell` |
| SystemUI-core.jar          | `m SystemUI-core`       |
| framework-15.jar           | `m framework`           |
| framework-14.jar           | `m framework`           |
| framework-13.jar           | `m framework`           |
| framework-12l.jar          | `m framework`           |
| framework-12.jar           | `m framework`           |
| framework-11.jar           | `m framework`           |
| framework-10.jar           | `m framework`           |

Location of the generated JARs:

| Module              | Path                                                                                                                             |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Framework           | ./soong/.intermediates/frameworks/base/framework/android_common/turbine-combined/framework.jar                                   |
| SystemUI-StatsD     | ./soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar         |
| WindowManager-Shell | ./soong/.intermediates/frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar |
| SystemUI-Core       | ./soong/.intermediates/frameworks/base/packages/SystemUI/SystemUI-core/android_common/javac/SystemUI-core.jar                    |

Any other JARs not listed here are kept for historical or reference purposes.
