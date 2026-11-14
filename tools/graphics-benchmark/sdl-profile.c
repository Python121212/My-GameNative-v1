/* 試験用 SDL2 プロキシ。指定回数の SDL_RenderPresent 直前を読み戻す。 */
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct { int x, y, w, h; } Rect;
typedef struct { uint64_t calls, ticks, max_ticks, logical_bytes; } Stat;
enum { LOCK, BLIT, UNLOCK, TARGET, COPY, PRESENT, N_STATS };
static Stat stats[N_STATS];
static unsigned frame_count;
static int profile_enabled = -1;
static int enabled(void) {
    if (profile_enabled < 0) {
        char value[8]; DWORD n = GetEnvironmentVariableA("GN_SDL_PROFILE", value, sizeof(value));
        profile_enabled = n == 1 && value[0] == '1';
    }
    return profile_enabled;
}
/* 前半30フレームと画像読み戻し対象フレームを除く。 */
static int sampling(void) { return enabled() && frame_count >= 30 && frame_count != 119; }
static int64_t now(void) { LARGE_INTEGER t; QueryPerformanceCounter(&t); return t.QuadPart; }
static void record(int id, int64_t start, int active) {
    if (!active) return;
    uint64_t dt = (uint64_t)(now() - start);
    Stat *s = &stats[id]; s->calls++; s->ticks += dt; if (dt > s->max_ticks) s->max_ticks = dt;
}
typedef struct { const char *name; uint32_t flags, num_texture_formats, texture_formats[16]; int max_texture_width, max_texture_height; } RendererInfo;
static HMODULE original(void) {
    HMODULE m=GetModuleHandleA("SDL2_real.dll");
    return m ? m : LoadLibraryA("SDL2_real.dll");
}
static FARPROC sym(const char *name) { HMODULE m=original(); return m ? GetProcAddress(m,name) : NULL; }
__declspec(dllexport) void *SDL_CreateRenderer(void *window, int index, uint32_t flags) {
    typedef void *(*fn_t)(void*,int,uint32_t);
    typedef int (*info_t)(void*,RendererInfo*);
    fn_t fn=(fn_t)sym("SDL_CreateRenderer"); info_t info=(info_t)sym("SDL_GetRendererInfo");
    if(!fn || !info) return NULL;
    void *renderer=fn(window,index,flags);
    RendererInfo r={0};
    if(renderer && info(renderer,&r)==0 && r.name) { FILE *f=fopen("renderer.txt","w"); if(f) { fprintf(f,"%s\n",r.name); fclose(f); } }
    return renderer;
}
__declspec(dllexport) void SDL_RenderPresent(void *renderer) {
    typedef void (*present_t)(void*);
    typedef int (*size_t_fn)(void*,int*,int*);
    typedef int (*read_t)(void*,const void*,uint32_t,void*,int);
    int active=sampling();
    ++frame_count;
    present_t present=(present_t)sym("SDL_RenderPresent");
    if(frame_count==120) {
        size_t_fn size=(size_t_fn)sym("SDL_GetRendererOutputSize"); read_t read=(read_t)sym("SDL_RenderReadPixels");
        int w=0,h=0;
        if(size && read && size(renderer,&w,&h)==0 && w>0 && h>0 && w<=4096 && h<=4096) {
            size_t bytes=(size_t)w*(size_t)h*4; void *pixels=malloc(bytes);
            /* SDL_PIXELFORMAT_ARGB8888; little-endian data is BGRA. */
            if(pixels && read(renderer,NULL,0x16362004u,pixels,w*4)==0) {
                FILE *f=fopen("frame.raw","wb"); if(f) { uint32_t dims[2]={(uint32_t)w,(uint32_t)h}; fwrite(dims,sizeof(dims),1,f); fwrite(pixels,1,bytes,f); fclose(f); }
            }
            free(pixels);
        }
    }
    int64_t start=active ? now() : 0;
    if(present) present(renderer);
    record(PRESENT,start,active);
}

__declspec(dllexport) int SDL_LockTexture(void *texture, const Rect *rect, void **pixels, int *pitch) {
    typedef int (*fn_t)(void*,const Rect*,void**,int*);
    typedef int (*query_t)(void*,uint32_t*,int*,int*,int*);
    fn_t fn=(fn_t)sym("SDL_LockTexture");
    int active=sampling(); int64_t start=active ? now() : 0;
    int ret=fn ? fn(texture,rect,pixels,pitch) : -1;
    record(LOCK,start,active);
    if(active && ret==0) {
        query_t query=(query_t)sym("SDL_QueryTexture"); uint32_t format=0; int access=0,w=0,h=0;
        if(query && query(texture,&format,&access,&w,&h)==0) {
            if(rect) { w=rect->w; h=rect->h; }
            if(w>0 && h>0) stats[LOCK].logical_bytes+=(uint64_t)w*(uint64_t)h*(format & 0xffu);
        }
    }
    return ret;
}
__declspec(dllexport) void SDL_UnlockTexture(void *texture) {
    typedef void (*fn_t)(void*); fn_t fn=(fn_t)sym("SDL_UnlockTexture");
    int active=sampling(); int64_t start=active ? now() : 0; if(fn) fn(texture); record(UNLOCK,start,active);
}
__declspec(dllexport) int SDL_LowerBlit(void *src, const Rect *sr, void *dst, Rect *dr) {
    typedef int (*fn_t)(void*,const Rect*,void*,Rect*); fn_t fn=(fn_t)sym("SDL_LowerBlit");
    int active=sampling(); int64_t start=active ? now() : 0; int ret=fn ? fn(src,sr,dst,dr) : -1; record(BLIT,start,active); return ret;
}
__declspec(dllexport) int SDL_SetRenderTarget(void *renderer, void *texture) {
    typedef int (*fn_t)(void*,void*); fn_t fn=(fn_t)sym("SDL_SetRenderTarget");
    int active=sampling(); int64_t start=active ? now() : 0; int ret=fn ? fn(renderer,texture) : -1; record(TARGET,start,active); return ret;
}
__declspec(dllexport) int SDL_RenderCopy(void *renderer, void *texture, const Rect *src, const Rect *dst) {
    typedef int (*fn_t)(void*,void*,const Rect*,const Rect*); fn_t fn=(fn_t)sym("SDL_RenderCopy");
    int active=sampling(); int64_t start=active ? now() : 0; int ret=fn ? fn(renderer,texture,src,dst) : -1; record(COPY,start,active); return ret;
}
__declspec(dllexport) void SDL_Quit(void) {
    typedef void (*fn_t)(void); fn_t fn=(fn_t)sym("SDL_Quit");
    if(enabled()) {
        static const char *names[N_STATS]={"lock","blit","unlock","target","copy","present"};
        LARGE_INTEGER freq; QueryPerformanceFrequency(&freq);
        FILE *f=fopen("sdl-profile.csv","w");
        if(f) {
            fprintf(f,"name,calls,ticks,max_ticks,logical_bytes,frequency\n");
            for(int i=0;i<N_STATS;i++) fprintf(f,"%s,%llu,%llu,%llu,%llu,%lld\n",names[i],(unsigned long long)stats[i].calls,(unsigned long long)stats[i].ticks,(unsigned long long)stats[i].max_ticks,(unsigned long long)stats[i].logical_bytes,(long long)freq.QuadPart);
            fclose(f);
        }
    }
    if(fn) fn();
}
