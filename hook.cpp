#include <dlfcn.h>
#include <sys/system_properties.h>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#define ALOG(...) __android_log_print(ANDROID_LOG_INFO, "LuminaGFX", __VA_ARGS__)

typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef float GLfloat;
typedef void (*EGLFuncPointer)();
typedef void* (*PFNEGLGETNEXTLAYERPROCADDRESSPROC)(void*, const char*);

#define GL_TEXTURE_2D               0x0DE1
#define GL_TEXTURE_2D_ARRAY         0x8D41
#define GL_TEXTURE_MAG_FILTER       0x2800
#define GL_TEXTURE_MIN_FILTER       0x2801
#define GL_NEAREST                  0x2600
#define GL_NEAREST_MIPMAP_NEAREST   0x2700
#define GL_NEAREST_MIPMAP_LINEAR    0x2702
#define GL_TEXTURE_BASE_LEVEL       0x813C
#define GL_TEXTURE_LOD_BIAS         0x8501

static void* g_layer_id = nullptr;
static PFNEGLGETNEXTLAYERPROCADDRESSPROC g_get_next_layer_proc_address = nullptr;

static void (*orig_glTexParameteri)(GLenum target, GLenum pname, GLint param) = nullptr;
static void (*orig_glTexParameterf)(GLenum target, GLenum pname, GLfloat param) = nullptr;
static void (*orig_glSamplerParameteri)(GLuint sampler, GLenum pname, GLint param) = nullptr;
static void (*orig_glSamplerParameterf)(GLuint sampler, GLenum pname, GLfloat param) = nullptr;

static int g_enabled = 0;
static int g_detail = 0;
static int g_sharp = 0;
static int g_hard_distance = 0;

static void update_config() {
    char prop_buf[PROP_VALUE_MAX];
    g_enabled = (__system_property_get("debug.lumina.gfx.enabled", prop_buf) > 0) ? atoi(prop_buf) : 0;
    g_detail = (__system_property_get("debug.lumina.gfx.detail", prop_buf) > 0) ? atoi(prop_buf) : 0;
    g_sharp = (__system_property_get("debug.lumina.gfx.sharp", prop_buf) > 0) ? atoi(prop_buf) : 0;
    g_hard_distance = (__system_property_get("debug.lumina.gfx.hard", prop_buf) > 0) ? atoi(prop_buf) : 0;
}

static void hook_glTexParameteri(GLenum target, GLenum pname, GLint param) {
    update_config();
    if (g_enabled && (target == GL_TEXTURE_2D || target == GL_TEXTURE_2D_ARRAY)) {
        if (pname == GL_TEXTURE_MIN_FILTER) {
            if (g_detail > 0 && orig_glTexParameteri) {
                orig_glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, g_detail);
            }
            if (g_detail > 0 && orig_glTexParameterf) {
                orig_glTexParameterf(target, GL_TEXTURE_LOD_BIAS, (GLfloat)(g_detail * 2.5f));
            }
            if (g_sharp) {
                param = g_hard_distance ? GL_NEAREST_MIPMAP_NEAREST : GL_NEAREST_MIPMAP_LINEAR;
            }
        } else if (pname == GL_TEXTURE_MAG_FILTER) {
            if (g_sharp) {
                param = GL_NEAREST;
            }
        }
    }
    if (orig_glTexParameteri) {
        orig_glTexParameteri(target, pname, param);
    }
}

static void hook_glTexParameterf(GLenum target, GLenum pname, GLfloat param) {
    update_config();
    if (g_enabled && (target == GL_TEXTURE_2D || target == GL_TEXTURE_2D_ARRAY)) {
        if (pname == GL_TEXTURE_LOD_BIAS && g_detail > 0) {
            param = (GLfloat)(g_detail * 2.5f);
        }
    }
    if (orig_glTexParameterf) {
        orig_glTexParameterf(target, pname, param);
    }
}

static void hook_glSamplerParameteri(GLuint sampler, GLenum pname, GLint param) {
    update_config();
    if (g_enabled) {
        if (pname == GL_TEXTURE_MIN_FILTER) {
            if (g_detail > 0 && orig_glSamplerParameterf) {
                orig_glSamplerParameterf(sampler, GL_TEXTURE_LOD_BIAS, (GLfloat)(g_detail * 2.5f));
            }
            if (g_sharp) {
                param = g_hard_distance ? GL_NEAREST_MIPMAP_NEAREST : GL_NEAREST_MIPMAP_LINEAR;
            }
        } else if (pname == GL_TEXTURE_MAG_FILTER) {
            if (g_sharp) {
                param = GL_NEAREST;
            }
        }
    }
    if (orig_glSamplerParameteri) {
        orig_glSamplerParameteri(sampler, pname, param);
    }
}

static void hook_glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param) {
    update_config();
    if (g_enabled && pname == GL_TEXTURE_LOD_BIAS && g_detail > 0) {
        param = (GLfloat)(g_detail * 2.5f);
    }
    if (orig_glSamplerParameterf) {
        orig_glSamplerParameterf(sampler, pname, param);
    }
}

extern "C" {

__attribute__((visibility("default")))
void* AndroidGLESLayer_Initialize(void* layer_id, PFNEGLGETNEXTLAYERPROCADDRESSPROC get_next_layer_proc_address) {
    g_layer_id = layer_id;
    g_get_next_layer_proc_address = get_next_layer_proc_address;
    ALOG("LuminaGFX Layer Initialized");
    update_config();
    return nullptr;
}

__attribute__((visibility("default")))
void* AndroidGLESLayer_GetProcAddress(const char* funcName, EGLFuncPointer next) {
    if (!funcName) return (void*)next;

    if (strcmp(funcName, "glTexParameteri") == 0) {
        orig_glTexParameteri = (void (*)(GLenum, GLenum, GLint))next;
        return (void*)hook_glTexParameteri;
    }
    if (strcmp(funcName, "glTexParameterf") == 0) {
        orig_glTexParameterf = (void (*)(GLenum, GLenum, GLfloat))next;
        return (void*)hook_glTexParameterf;
    }
    if (strcmp(funcName, "glSamplerParameteri") == 0) {
        orig_glSamplerParameteri = (void (*)(GLuint, GLenum, GLint))next;
        return (void*)hook_glSamplerParameteri;
    }
    if (strcmp(funcName, "glSamplerParameterf") == 0) {
        orig_glSamplerParameterf = (void (*)(GLuint, GLenum, GLfloat))next;
        return (void*)hook_glSamplerParameterf;
    }
    return (void*)next;
}

__attribute__((visibility("default")))
void glTexParameteri(GLenum target, GLenum pname, GLint param) {
    hook_glTexParameteri(target, pname, param);
}

__attribute__((visibility("default")))
void glTexParameterf(GLenum target, GLenum pname, GLfloat param) {
    hook_glTexParameterf(target, pname, param);
}

__attribute__((visibility("default")))
void glSamplerParameteri(GLuint sampler, GLenum pname, GLint param) {
    hook_glSamplerParameteri(sampler, pname, param);
}

__attribute__((visibility("default")))
void glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param) {
    hook_glSamplerParameterf(sampler, pname, param);
}

}
