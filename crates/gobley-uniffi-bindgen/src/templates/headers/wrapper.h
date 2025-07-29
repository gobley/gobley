#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

{% include "RustBufferTemplate.h" %}
{% include "Helpers.h" %}

// Public interface members begin here.
{{ type_helper_code }}

// Contains loading, initialization code,
// and the FFI Function declarations.
{% include "NamespaceLibraryTemplate.h" %}
