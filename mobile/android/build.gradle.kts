allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

// camera-core, CallbackToFutureAdapter'ı public imzasında kullanıyor ama POM'da
// yalnızca `implementation` olarak bildiriyor. AGP 9 / javac bu sınıfı derleme
// classpath'inde bulamayınca camera_android_camerax derlemesi kırılıyor.
subprojects {
    if (name == "camera_android_camerax") {
        plugins.withId("com.android.library") {
            dependencies.add(
                "compileOnly",
                "androidx.concurrent:concurrent-futures:1.2.0",
            )
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
