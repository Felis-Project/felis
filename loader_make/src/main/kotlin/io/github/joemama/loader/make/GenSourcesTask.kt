	package io.github.joemama.loader.make
	
	import org.benf.cfr.reader.api.CfrDriver
	import org.benf.cfr.reader.util.getopt.OptionsImpl
	import org.gradle.api.DefaultTask
	import org.gradle.api.file.RegularFileProperty
	import org.gradle.api.tasks.InputFile
	import org.gradle.api.tasks.OutputDirectory
	import org.gradle.api.tasks.TaskAction
	
	abstract class GenSourcesTask : DefaultTask() {
	    @get:InputFile
	    abstract val inputJar: RegularFileProperty
	
	    @get:OutputDirectory
	    abstract val outputJar: RegularFileProperty
	
	    @TaskAction
	    fun genSources() {
	        val output = this.outputJar.get().asFile.path
	        println("Decompiling sources to $output")
	        val cfr = CfrDriver.Builder()
	            .withOptions(
	                mapOf(
	                    OptionsImpl.OUTPUT_PATH.name to output,
	                    OptionsImpl.SILENT.name to "true",
	                    OptionsImpl.CLOBBER_FILES.name to "true"
	                )
	            ).build()
	        cfr.analyse(listOf(this.inputJar.get().asFile.path))
	        println("Finished decompiling sources")
	    }
	}