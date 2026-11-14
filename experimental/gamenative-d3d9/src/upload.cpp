// SPDX-License-Identifier: GPL-3.0-or-later
#include "upload.hpp"
#include <algorithm>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <string>
#include <utility>

namespace gn::d3d9 {
namespace {
void check(VkResult result, const char* action) {
    if (result != VK_SUCCESS) throw std::runtime_error(std::string(action) + ": VkResult=" + std::to_string(result));
}
struct Buffer { VkBuffer buffer = VK_NULL_HANDLE; VkDeviceMemory memory = VK_NULL_HANDLE; void* mapped = nullptr; bool coherent = false; };
struct Texture { VkImage image = VK_NULL_HANDLE; VkDeviceMemory memory = VK_NULL_HANDLE; VkImageLayout layout = VK_IMAGE_LAYOUT_UNDEFINED; uint32_t width = 0, height = 0; };
struct Update { size_t texture; UploadContext::Rect rect; VkDeviceSize offset; };
}

struct UploadContext::Impl {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    VkPhysicalDeviceMemoryProperties memoryProps{};
    VkPhysicalDeviceProperties props{};
    Buffer staging;
    VkDeviceSize capacity = 0, used = 0;
    bool pending = false, locked = false;
    Update active{};
    std::vector<Texture> textures;
    std::vector<Update> updates;

    ~Impl() {
        if (device) {
            vkDeviceWaitIdle(device);
            destroyBuffer(staging);
            for (auto& t : textures) { if (t.image) vkDestroyImage(device, t.image, nullptr); if (t.memory) vkFreeMemory(device, t.memory, nullptr); }
            if (fence) vkDestroyFence(device, fence, nullptr);
            if (pool) vkDestroyCommandPool(device, pool, nullptr);
            vkDestroyDevice(device, nullptr);
        }
        if (instance) vkDestroyInstance(instance, nullptr);
    }
    void destroyBuffer(Buffer& b) {
        if (b.mapped) vkUnmapMemory(device, b.memory);
        if (b.buffer) vkDestroyBuffer(device, b.buffer, nullptr);
        if (b.memory) vkFreeMemory(device, b.memory, nullptr);
        b = {};
    }
    uint32_t memoryType(uint32_t mask, VkMemoryPropertyFlags required, VkMemoryPropertyFlags preferred) {
        uint32_t fallback = UINT32_MAX;
        for (uint32_t i = 0; i < memoryProps.memoryTypeCount; ++i) {
            auto flags = memoryProps.memoryTypes[i].propertyFlags;
            if (!(mask & (1u << i)) || (flags & required) != required) continue;
            if ((flags & preferred) == preferred) return i;
            if (fallback == UINT32_MAX) fallback = i;
        }
        if (fallback == UINT32_MAX) throw std::runtime_error("利用可能な Vulkan メモリ種別がありません");
        return fallback;
    }
    Buffer makeBuffer(VkDeviceSize bytes, VkBufferUsageFlags usage) {
        Buffer b;
        try {
            VkBufferCreateInfo info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO}; info.size = bytes; info.usage = usage; info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            check(vkCreateBuffer(device, &info, nullptr, &b.buffer), "vkCreateBuffer");
            VkMemoryRequirements req{}; vkGetBufferMemoryRequirements(device, b.buffer, &req);
            auto index = memoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            b.coherent = (memoryProps.memoryTypes[index].propertyFlags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0;
            VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; alloc.allocationSize = req.size; alloc.memoryTypeIndex = index;
            check(vkAllocateMemory(device, &alloc, nullptr, &b.memory), "vkAllocateMemory");
            check(vkBindBufferMemory(device, b.buffer, b.memory, 0), "vkBindBufferMemory");
            check(vkMapMemory(device, b.memory, 0, VK_WHOLE_SIZE, 0, &b.mapped), "vkMapMemory");
            return b;
        } catch (...) { destroyBuffer(b); throw; }
    }
    void flush(Buffer& b) {
        if (!b.coherent) { VkMappedMemoryRange range{VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE}; range.memory=b.memory; range.offset=0; range.size=VK_WHOLE_SIZE; check(vkFlushMappedMemoryRanges(device, 1, &range), "vkFlushMappedMemoryRanges"); }
    }
    void invalidate(Buffer& b) {
        if (!b.coherent) { VkMappedMemoryRange range{VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE}; range.memory=b.memory; range.offset=0; range.size=VK_WHOLE_SIZE; check(vkInvalidateMappedMemoryRanges(device, 1, &range), "vkInvalidateMappedMemoryRanges"); }
    }
    void wait() {
        if (pending) { check(vkWaitForFences(device, 1, &fence, VK_TRUE, UINT64_MAX), "vkWaitForFences"); pending=false; used=0; }
    }
    void begin() {
        wait();
        check(vkResetCommandPool(device, pool, 0), "vkResetCommandPool");
        VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO}; bi.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        check(vkBeginCommandBuffer(cmd, &bi), "vkBeginCommandBuffer");
    }
    void endSubmit() {
        check(vkEndCommandBuffer(cmd), "vkEndCommandBuffer");
        check(vkResetFences(device, 1, &fence), "vkResetFences");
        VkSubmitInfo si{VK_STRUCTURE_TYPE_SUBMIT_INFO}; si.commandBufferCount=1; si.pCommandBuffers=&cmd;
        check(vkQueueSubmit(queue, 1, &si, fence), "vkQueueSubmit"); pending=true;
    }
    void transition(Texture& t, VkImageLayout target) {
        if (t.layout == target) return;
        VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        b.oldLayout=t.layout; b.newLayout=target; b.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        b.image=t.image; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        b.srcAccessMask = t.layout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL ? VK_ACCESS_TRANSFER_WRITE_BIT : t.layout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL ? VK_ACCESS_TRANSFER_READ_BIT : 0;
        b.dstAccessMask = target == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL ? VK_ACCESS_TRANSFER_WRITE_BIT : VK_ACCESS_TRANSFER_READ_BIT;
        VkPipelineStageFlags src = t.layout == VK_IMAGE_LAYOUT_UNDEFINED ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_TRANSFER_BIT;
        vkCmdPipelineBarrier(cmd, src, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,nullptr,0,nullptr,1,&b);
        t.layout=target;
    }
    Texture& texture(TextureId id) { if (id >= textures.size()) throw std::invalid_argument("テクスチャ ID が不正です"); return textures[id]; }
};

UploadContext::UploadContext(VkDeviceSize bytes) : impl(std::make_unique<Impl>()) {
    if (bytes < 4 || bytes > std::numeric_limits<size_t>::max())
        throw std::invalid_argument("ステージング容量が不正です");
    auto& p=*impl; p.capacity=bytes;
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO}; app.pApplicationName="GameNative 実験転送基盤"; app.apiVersion=VK_API_VERSION_1_0;
    VkInstanceCreateInfo ii{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; ii.pApplicationInfo=&app;
    check(vkCreateInstance(&ii,nullptr,&p.instance),"vkCreateInstance");
    uint32_t count=0; check(vkEnumeratePhysicalDevices(p.instance,&count,nullptr),"vkEnumeratePhysicalDevices");
    if (!count) throw std::runtime_error("Vulkan デバイスがありません");
    std::vector<VkPhysicalDevice> devices(count); check(vkEnumeratePhysicalDevices(p.instance,&count,devices.data()),"vkEnumeratePhysicalDevices");
    uint32_t family=UINT32_MAX;
    for (auto d : devices) {
        uint32_t n=0; vkGetPhysicalDeviceQueueFamilyProperties(d,&n,nullptr); std::vector<VkQueueFamilyProperties> families(n); vkGetPhysicalDeviceQueueFamilyProperties(d,&n,families.data());
        for(uint32_t i=0;i<n;++i) if(families[i].queueCount && (families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT)) { p.physical=d; family=i; break; }
        if(p.physical) break;
    }
    if(!p.physical) throw std::runtime_error("描画キューがありません");
    vkGetPhysicalDeviceMemoryProperties(p.physical,&p.memoryProps); vkGetPhysicalDeviceProperties(p.physical,&p.props);
    float priority=1.0f; VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO}; qi.queueFamilyIndex=family; qi.queueCount=1; qi.pQueuePriorities=&priority;
    VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO}; di.queueCreateInfoCount=1; di.pQueueCreateInfos=&qi;
    check(vkCreateDevice(p.physical,&di,nullptr,&p.device),"vkCreateDevice"); vkGetDeviceQueue(p.device,family,0,&p.queue);
    VkCommandPoolCreateInfo pi{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO}; pi.queueFamilyIndex=family; pi.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    check(vkCreateCommandPool(p.device,&pi,nullptr,&p.pool),"vkCreateCommandPool");
    VkCommandBufferAllocateInfo ai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO}; ai.commandPool=p.pool; ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandBufferCount=1;
    check(vkAllocateCommandBuffers(p.device,&ai,&p.cmd),"vkAllocateCommandBuffers");
    VkFenceCreateInfo fi{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO}; check(vkCreateFence(p.device,&fi,nullptr,&p.fence),"vkCreateFence");
    p.staging=p.makeBuffer(bytes,VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
}
UploadContext::~UploadContext() = default;

UploadContext::TextureId UploadContext::createTexture(uint32_t w,uint32_t h,Format fmt) {
    auto& p=*impl;
    if (!w || !h || w>p.props.limits.maxImageDimension2D || h>p.props.limits.maxImageDimension2D
        || w>INT32_MAX || h>INT32_MAX || p.textures.size()>=UINT32_MAX)
        throw std::invalid_argument("テクスチャサイズが不正です");
    VkFormat vkFormat;
    switch (fmt) {
    case Format::Rgba8: vkFormat=VK_FORMAT_R8G8B8A8_UNORM; break;
    case Format::Bgra8: vkFormat=VK_FORMAT_B8G8R8A8_UNORM; break;
    default: throw std::invalid_argument("未対応の画像形式です");
    }
    VkFormatProperties format{}; vkGetPhysicalDeviceFormatProperties(p.physical,vkFormat,&format);
    const VkFormatFeatureFlags required=VK_FORMAT_FEATURE_TRANSFER_SRC_BIT | VK_FORMAT_FEATURE_TRANSFER_DST_BIT | VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
    if ((format.optimalTilingFeatures & required) != required) throw std::runtime_error("指定画像形式で必要な転送・サンプリングに未対応です");
    Texture t; t.width=w; t.height=h;
    try {
        VkImageCreateInfo ci{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO}; ci.imageType=VK_IMAGE_TYPE_2D; ci.format=vkFormat; ci.extent={w,h,1}; ci.mipLevels=1; ci.arrayLayers=1; ci.samples=VK_SAMPLE_COUNT_1_BIT; ci.tiling=VK_IMAGE_TILING_OPTIMAL; ci.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT; ci.sharingMode=VK_SHARING_MODE_EXCLUSIVE; ci.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        check(vkCreateImage(p.device,&ci,nullptr,&t.image),"vkCreateImage");
        VkMemoryRequirements req{}; vkGetImageMemoryRequirements(p.device,t.image,&req);
        VkMemoryAllocateInfo ai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; ai.allocationSize=req.size; ai.memoryTypeIndex=p.memoryType(req.memoryTypeBits,0,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        check(vkAllocateMemory(p.device,&ai,nullptr,&t.memory),"画像メモリ割当"); check(vkBindImageMemory(p.device,t.image,t.memory,0),"vkBindImageMemory");
        p.textures.push_back(t);
    } catch (...) { if(t.image) vkDestroyImage(p.device,t.image,nullptr); if(t.memory) vkFreeMemory(p.device,t.memory,nullptr); throw; }
    return static_cast<TextureId>(p.textures.size()-1);
}
UploadContext::Lock UploadContext::lock(TextureId id,Rect r) {
    auto& p=*impl; if(p.locked) throw std::logic_error("既にロック中です");
    auto& t=p.texture(id);
    if (!r.width || !r.height || r.x>t.width || r.y>t.height || r.width>t.width-r.x || r.height>t.height-r.y || r.width>UINT32_MAX/4) throw std::invalid_argument("更新矩形が不正です");
    if (p.pending) p.wait();
    VkDeviceSize row=VkDeviceSize(r.width)*4, size=row*r.height;
    if (p.used > p.capacity || size > p.capacity-p.used) throw std::length_error("ステージング容量不足。送信してから再試行してください");
    p.active={id,r,p.used}; p.used+=size; p.locked=true;
    return {static_cast<unsigned char*>(p.staging.mapped)+p.active.offset, static_cast<uint32_t>(row)};
}
void UploadContext::unlock() { auto& p=*impl; if(!p.locked) throw std::logic_error("ロックされていません"); p.updates.push_back(p.active); p.locked=false; }
void UploadContext::submit() {
    auto& p=*impl; if(p.locked) throw std::logic_error("ロック中は送信できません"); if(p.updates.empty()) return;
    p.flush(p.staging); p.begin();
    for(const auto& u : p.updates) {
        auto& t=p.textures[u.texture];
        // 同じ画像への複数コピーは重複矩形もあり得るので、書き込み順を明示する。
        if(t.layout==VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER}; b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.oldLayout=b.newLayout=t.layout; b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; b.image=t.image; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            vkCmdPipelineBarrier(p.cmd,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,0,nullptr,1,&b);
        } else p.transition(t,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        VkBufferImageCopy c{}; c.bufferOffset=u.offset; c.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1}; c.imageOffset={static_cast<int32_t>(u.rect.x),static_cast<int32_t>(u.rect.y),0}; c.imageExtent={u.rect.width,u.rect.height,1};
        vkCmdCopyBufferToImage(p.cmd,p.staging.buffer,t.image,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,1,&c);
    }
    p.endSubmit(); p.updates.clear();
}
std::vector<uint8_t> UploadContext::readback(TextureId id) {
    auto& p=*impl; if(p.locked) throw std::logic_error("ロック中は読み戻せません");
    p.texture(id); submit(); p.wait(); auto& t=p.texture(id);
    if(t.layout==VK_IMAGE_LAYOUT_UNDEFINED) throw std::logic_error("未初期化の画像は読み戻せません");
    uint64_t bytes=uint64_t(t.width)*t.height*4;
    if(bytes>std::numeric_limits<size_t>::max()) throw std::length_error("画像が大きすぎます");
    std::vector<uint8_t> result(static_cast<size_t>(bytes));
    Buffer out=p.makeBuffer(bytes,VK_BUFFER_USAGE_TRANSFER_DST_BIT);
    try {
        p.begin(); p.transition(t,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        VkBufferImageCopy c{}; c.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1}; c.imageExtent={t.width,t.height,1};
        vkCmdCopyImageToBuffer(p.cmd,t.image,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,out.buffer,1,&c);
        VkBufferMemoryBarrier b{VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER}; b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_HOST_READ_BIT; b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; b.buffer=out.buffer; b.offset=0; b.size=VK_WHOLE_SIZE;
        vkCmdPipelineBarrier(p.cmd,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,0,nullptr,1,&b,0,nullptr);
        p.endSubmit(); p.wait(); p.invalidate(out); std::memcpy(result.data(),out.mapped,result.size()); p.destroyBuffer(out);
    } catch (...) { if(p.device) vkDeviceWaitIdle(p.device); p.destroyBuffer(out); throw; }
    return result;
}
void UploadContext::wait() { auto& p=*impl; if(p.locked) throw std::logic_error("ロック中は待機できません"); p.wait(); }
}
