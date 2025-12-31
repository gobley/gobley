
// Define FFI callback types
{%- let lib_private_fun_indent %}
{%- if config.enable_jna_interface_mapping() %}
    {%- let lib_private_fun_indent = 8 %}
{%- else %}
    {%- let lib_private_fun_indent = 4 %}
{%- endif %}
{%- let dynamic_library_dependencies = config.dynamic_library_dependencies(module_name) %}
{%- for def in ci.ffi_definitions() %}
{%- match def %}
{%- when FfiDefinition::CallbackFunction(callback) %}
internal interface {{ callback.name()|ffi_callback_name }}: com.sun.jna.Callback {
    public fun callback(
        {%- for arg in callback.arguments() -%}
        {{ arg.name().borrow()|var_name }}: {{ arg.type_().borrow()|ffi_type_name_by_value(ci) }},
        {%- endfor -%}
        {%- if callback.has_rust_call_status_arg() -%}
        uniffiCallStatus: UniffiRustCallStatus,
        {%- endif -%}
    )
    {%- if let Some(return_type) = callback.return_type() -%}
    : {{ return_type|ffi_type_name_by_value(ci) }}
    {%- endif %}
}
{%- when FfiDefinition::Struct(ffi_struct) %}
@Structure.FieldOrder({% for field in ffi_struct.fields() %}"{{ field.name()|var_name_raw }}"{% if !loop.last %}, {% endif %}{% endfor %})
internal open class {{ ffi_struct.name()|ffi_struct_name }}Struct(
    {%- for field in ffi_struct.fields() %}
    @JvmField public var {{ field.name()|var_name }}: {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(ci) }},
    {%- endfor %}
) : com.sun.jna.Structure() {
    internal constructor(): this(
        {% for field in ffi_struct.fields() %}
        {{ field.name()|var_name }} = {{ field.type_()|ffi_default_value }},
        {% endfor %}
    )

    internal class UniffiByValue(
        {%- for field in ffi_struct.fields() %}
        {{ field.name()|var_name }}: {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(ci) }},
        {%- endfor %}
    ): {{ ffi_struct.name()|ffi_struct_name }}({%- for field in ffi_struct.fields() %}{{ field.name()|var_name }}, {%- endfor %}), Structure.ByValue
}

internal typealias {{ ffi_struct.name()|ffi_struct_name }} = {{ ffi_struct.name()|ffi_struct_name }}Struct

internal fun {{ ffi_struct.name()|ffi_struct_name }}.uniffiSetValue(other: {{ ffi_struct.name()|ffi_struct_name }}) {
    {%- for field in ffi_struct.fields() %}
    {{ field.name()|var_name }} = other.{{ field.name()|var_name }}
    {%- endfor %}
}
internal fun {{ ffi_struct.name()|ffi_struct_name }}.uniffiSetValue(other: {{ ffi_struct.name()|ffi_struct_name }}UniffiByValue) {
    {%- for field in ffi_struct.fields() %}
    {{ field.name()|var_name }} = other.{{ field.name()|var_name }}
    {%- endfor %}
}

internal typealias {{ ffi_struct.name()|ffi_struct_name }}UniffiByValue = {{ ffi_struct.name()|ffi_struct_name }}Struct.UniffiByValue

{%- when FfiDefinition::Function(_) %}
{# functions are handled below #}
{%- endmatch %}
{%- endfor %}

@Synchronized
private fun findLibraryName(componentName: String): String {
    val libOverride = System.getProperty("uniffi.component.$componentName.libraryOverride")
    if (libOverride != null) {
        return libOverride
    }
    return "{{ config.cdylib_name() }}"
}

{%- if config.enable_jna_interface_mapping() %}
private inline fun <reified Lib : Library> loadIndirect(componentName: String): Lib {
    return Native.load<Lib>(findLibraryName(componentName), Lib::class.java)
}
{%- endif %}

{%- if !dynamic_library_dependencies.is_empty() %}
{%- if module_name == "jvm" %}
// Dynamic library dependency loading code.
//
// Load dynamic libraries that the main Rust library depends on. They may
// reside inside a .jar file, or already be installed in the file system.
// The reason for this custom handling is that JNA copies the extracted
// library to a temporary file with a random name, resulting in a dynamic
// link error on Windows. This logic ensures that the destination temporary
// file has the same base name as the original library file in the JAR file.
//
// First, try loading the library without searching the directories
// specified in CLASSPATH.
@Suppress("SameParameterValue")
private fun loadDynamicLibraryDependencies(vararg dependencies: String) {
    val nilClasspathClassLoader = java.net.URLClassLoader(emptyArray(), Any::class.java.classLoader)
    val dependenciesRequiringExtraction = mutableListOf<String>()
    for (dependency in dependencies) {
        try {
            com.sun.jna.NativeLibrary.getInstance(dependency, nilClasspathClassLoader)
        } catch (_: UnsatisfiedLinkError) {
            dependenciesRequiringExtraction.add(dependency)
        }
    }
    loadDynamicLibraryDependenciesByExtraction(dependenciesRequiringExtraction, nilClasspathClassLoader)
{{ '}' }}

// Second, try extracting the library from a .jar file.
private fun loadDynamicLibraryDependenciesByExtraction(
    dependencies: List<String>,
    nilClasspathClassLoader: ClassLoader,
) {
    if (dependencies.isEmpty()) return
    // The directory where the dynamic library dependencies in zipped JAR files will be extracted
    val extractionDestination = java.nio.file.Files.createTempDirectory("gobley-jna").toFile()
    val classLoader = UniffiLib::class.java.classLoader!!
    val dependenciesRequiringJnaHandling = mutableListOf<String>()
    for (dependency in dependencies) {
        val libraryFile = findLibraryInClassPath(dependency, classLoader, extractionDestination)
        if (libraryFile == null) {
            dependenciesRequiringJnaHandling.add(dependency)
            continue
        }
        try {
            com.sun.jna.NativeLibrary.addSearchPath(
                dependency,
                libraryFile.parentFile.absolutePath,
            )
            com.sun.jna.NativeLibrary.getInstance(
                dependency,
                nilClasspathClassLoader,
            )
        } catch (_: UnsatisfiedLinkError) {
            dependenciesRequiringJnaHandling.add(dependency)
        }
    }
    // Lastly, if all the logic above fails, try loading the library using JNA.
    for (dependency in dependenciesRequiringJnaHandling) {
        com.sun.jna.NativeLibrary.getInstance(dependency)
    }
{{ '}' }}

private fun findLibraryInClassPath(
    library: String,
    classLoader: ClassLoader,
    extractionDestination: java.io.File,
): java.io.File? {
    var libraryName = System.mapLibraryName(library)
    if (com.sun.jna.Platform.isMac()) {
        if (libraryName.endsWith(".jnilib")) {
            libraryName = libraryName.removeSuffix(".jnilib") + ".dylib"
        }
    }
    val resourcePath = "${com.sun.jna.Platform.RESOURCE_PREFIX}/$libraryName"
    var url = classLoader.getResource(resourcePath)
    if (com.sun.jna.Platform.isMac() && url == null) {
        url = classLoader.getResource("darwin/$libraryName")
    }
    if (url == null) {
        url = classLoader.getResource(libraryName)
    }
    if (url == null) {
        return null
    }
    if (url.protocol.equals("file", ignoreCase = true)) {
        val file = try {
            java.io.File(url.toURI())
        } catch (_: java.net.URISyntaxException) {
            java.io.File(url.path)
        }
        return file.takeIf { it.exists() }
    }
    val destination = extractionDestination.resolve(resourcePath).apply {
        parentFile?.mkdirs()
        deleteOnExit()
    }
    url.openStream().use { inputStream ->
        destination.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
    return destination
{{ '}' }}
{%- else %}
private fun loadDynamicLibraryDependencies(vararg dependencies: String) {
    for (dependency in dependencies) {
        com.sun.jna.NativeLibrary.getInstance(dependency)
    }
{{ '}' }}
{%- endif %}
{%- endif %}

// For large crates we prevent `MethodTooLargeException` (see #2340)
// N.B. the name of the extension is very misleading, since it is 
// rather `InterfaceTooLargeException`, caused by too many methods 
// in the interface for large crates.
//
// By splitting the otherwise huge interface into two parts
// * UniffiLib 
// * IntegrityCheckingUniffiLib (this)
// we allow for ~2x as many methods in the UniffiLib interface.
// 
// The `ffi_uniffi_contract_version` method and all checksum methods are put 
// into `IntegrityCheckingUniffiLib` and these methods are called only once,
// when the library is loaded.
{%- if config.enable_jna_interface_mapping() %}
internal interface IntegrityCheckingUniffiLib : Library {
    companion object : IntegrityCheckingUniffiLib by loadIndirect("{{ ci.namespace() }}") {
        init {
            uniffiCheckContractApiVersion()
            {%- if !config.omit_checksums %}
            uniffiCheckApiChecksums()
            {%- endif %}
        }
{%- else %}
internal object IntegrityCheckingUniffiLib : Library {
    init {
        Native.register(IntegrityCheckingUniffiLib::class.java, findLibraryName("{{ ci.namespace() }}"))
        uniffiCheckContractApiVersion()
        {%- if !config.omit_checksums %}
        uniffiCheckApiChecksums()
        {%- endif %}
    }
{%- endif %}

{{ " "|repeat(lib_private_fun_indent) }}private fun uniffiCheckContractApiVersion() {
{{ " "|repeat(lib_private_fun_indent) }}    // Get the bindings contract version from our ComponentInterface
{{ " "|repeat(lib_private_fun_indent) }}    val bindingsContractVersion = {{ ci.uniffi_contract_version() }}
{{ " "|repeat(lib_private_fun_indent) }}    // Get the scaffolding contract version by calling the into the dylib
{{ " "|repeat(lib_private_fun_indent) }}    val scaffoldingContractVersion = {{ ci.ffi_uniffi_contract_version().name() }}()
{{ " "|repeat(lib_private_fun_indent) }}    if (bindingsContractVersion != scaffoldingContractVersion) {
{{ " "|repeat(lib_private_fun_indent) }}        throw RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project")
{{ " "|repeat(lib_private_fun_indent) }}    }
{{ " "|repeat(lib_private_fun_indent) }}}

                                        {%- if !config.omit_checksums %}
                                        {%- if ci.iter_checksums().next().is_none() %}

{{ " "|repeat(lib_private_fun_indent) }}@Suppress("UNUSED_PARAMETER")
                                        {%- endif %}
{{ " "|repeat(lib_private_fun_indent) }}private fun uniffiCheckApiChecksums() {
                                            {%- for (name, expected_checksum) in ci.iter_checksums() %}
{{ " "|repeat(lib_private_fun_indent) }}    if ({{ name }}() != {{ expected_checksum }}.toShort()) {
{{ " "|repeat(lib_private_fun_indent) }}        throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
{{ " "|repeat(lib_private_fun_indent) }}    }
                                            {%- endfor %}
{{ " "|repeat(lib_private_fun_indent) }}{{ '}' }}
                                        {%- endif %}
    {%- if config.enable_jna_interface_mapping() %}
    }
    {%- endif %}

    // Integrity check functions only
    {%- for func in ci.iter_ffi_function_integrity_checks() %}
    {%- if config.enable_jna_interface_mapping() %}
    fun
    {%- else %}
    @JvmStatic
    external fun
    {%- endif %} {{ func.name() }}(
        {%- call kt::arg_list_ffi_decl(func, 8) %}
    ): {% match func.return_type() %}{% when Some(return_type) %}{{ return_type.borrow()|ffi_type_name_by_value(ci) }}{% when None %}Unit{% endmatch %}
    {%- endfor %}
}

// A JNA Library to expose the extern-C FFI definitions.
// This is an implementation detail which will be called internally by the public API.
{%- if config.enable_jna_interface_mapping() %}
internal interface UniffiLib : Library {
    companion object : UniffiLib by (
        run {
            {%- if !dynamic_library_dependencies.is_empty() %}
            loadDynamicLibraryDependencies(
                {%- for dynamic_library in config.dynamic_library_dependencies(module_name) %}
                "{{ dynamic_library }}",
                {%- endfor %}
                // Load the main library as well
                findLibraryName("{{ ci.namespace() }}"),
            )
            {%- endif %}
            IntegrityCheckingUniffiLib
            loadIndirect("{{ ci.namespace() }}")
        }
    ) {
        init {
            // No need to check the contract version and checksums, since 
            // we already did that with `IntegrityCheckingUniffiLib` above.
            {%- for init_fn in self.initialization_fns(ci) %}
            {{ init_fn }}
            {%- endfor %}
        }
{%- else %}
internal object UniffiLib : Library {
    init {
        {%- if !dynamic_library_dependencies.is_empty() %}
        loadDynamicLibraryDependencies(
            {%- for dynamic_library in config.dynamic_library_dependencies(module_name) %}
            "{{ dynamic_library }}",
            {%- endfor %}
            // Load the main library as well
            findLibraryName("{{ ci.namespace() }}"),
        )
        {%- endif %}
        IntegrityCheckingUniffiLib
        Native.register(UniffiLib::class.java, findLibraryName("{{ ci.namespace() }}"))
        // No need to check the contract version and checksums, since 
        // we already did that with `IntegrityCheckingUniffiLib` above.
        {%- for init_fn in self.initialization_fns(ci) %}
        {{ init_fn }}
        {%- endfor %}
    }
{%- endif %}

                                        {%- if ci.contains_object_types() %}
{{ " "|repeat(lib_private_fun_indent) }}// The Cleaner for the whole library
{{ " "|repeat(lib_private_fun_indent) }}internal val CLEANER: UniffiCleaner by lazy {
{{ " "|repeat(lib_private_fun_indent) }}    UniffiCleaner.create()
{{ " "|repeat(lib_private_fun_indent) }}{{ '}' }}
                                        {%- endif %}

    {%- if config.enable_jna_interface_mapping() %}
    }
    {%- endif %}

    {%- for func in ci.iter_ffi_function_definitions_excluding_integrity_checks() %}
    {%- if config.enable_jna_interface_mapping() %}
    fun
    {%- else %}
    @JvmStatic
    external fun
    {%- endif %} {{ func.name() }}(
        {%- call kt::arg_list_ffi_decl(func, 8) %}
    ): {% match func.return_type() %}{% when Some(return_type) %}{{ return_type.borrow()|ffi_type_name_by_value(ci) }}{% when None %}Unit{% endmatch %}
    {%- endfor %}
}

{{ visibility() }}fun uniffiEnsureInitialized() {
    UniffiLib
}
