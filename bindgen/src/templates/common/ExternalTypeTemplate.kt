
{%- let namespace = ci.namespace_for_module_path(module_path)? %}
{%- let package_name=self.external_type_package_name(module_path, namespace) %}
{%- let fully_qualified_type_name = "{}.{}"|format(package_name, name|class_name(ci)) %}
{{- self.add_import(fully_qualified_type_name) }}