
typedef struct RustBuffer
{
    int64_t capacity;
    int64_t len;
    uint8_t *_Nullable data;
} RustBuffer;

typedef struct RustBufferByReference
{
    int64_t capacity;
    int64_t len;
    uint8_t *_Nullable data;
} RustBufferByReference;

typedef struct ForeignBytes
{
    int32_t len;
    const uint8_t *_Nullable data;
} ForeignBytes;
