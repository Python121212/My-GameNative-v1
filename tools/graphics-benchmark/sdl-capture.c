/* 試験用 SDL2 プロキシ。指定回数の SDL_RenderPresent 直前を読み戻す。 */
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

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
    static unsigned frame=0; ++frame;
    present_t present=(present_t)sym("SDL_RenderPresent");
    if(frame==120) {
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
    if(present) present(renderer);
}
