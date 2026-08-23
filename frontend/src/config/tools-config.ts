import type { ToolMeta } from '@/api/types'

/**
 * 20 个常用工具元数据 (配置驱动 UI).
 * 新增工具只需在此追加一条, 配合 i18n key 即可被详情页通用渲染.
 *
 * 命名规范:
 * - id: kebab-case, 路由用
 * - nameKey: tools.<id>.name
 * - descKey: tools.<id>.desc
 * - 参数 labelKey: tools.<id>.param.<key>
 */
export const TOOL_LIST: ToolMeta[] = [
  // ========== 人像处理 (5) ==========
  {
    id: 'face-swap',
    category: 'portrait',
    icon: 'Avatar',
    nameKey: 'tools.face-swap.name',
    descKey: 'tools.face-swap.desc',
    input: 'multi-image',
    maxImages: 2,
    params: [
      {
        key: 'similarity',
        type: 'slider',
        labelKey: 'tools.face-swap.param.similarity',
        min: 50,
        max: 100,
        step: 5,
        default: 75,
      },
      {
        key: 'blendStrength',
        type: 'slider',
        labelKey: 'tools.face-swap.param.blendStrength',
        min: 0,
        max: 100,
        step: 5,
        default: 50,
      },
    ],
    cost: 8,
    badge: 'hot',
  },
  {
    id: 'change-emotion',
    category: 'portrait',
    icon: 'Sunny',
    nameKey: 'tools.change-emotion.name',
    descKey: 'tools.change-emotion.desc',
    input: 'single-image',
    params: [
      {
        key: 'emotion',
        type: 'select',
        labelKey: 'tools.change-emotion.param.emotion',
        options: [
          { value: 'happy', labelKey: 'tools.emotion.happy' },
          { value: 'sad', labelKey: 'tools.emotion.sad' },
          { value: 'angry', labelKey: 'tools.emotion.angry' },
          { value: 'surprised', labelKey: 'tools.emotion.surprised' },
          { value: 'cool', labelKey: 'tools.emotion.cool' },
        ],
        default: 'happy',
      },
    ],
    cost: 5,
  },
  {
    id: 'change-age',
    category: 'portrait',
    icon: 'Timer',
    nameKey: 'tools.change-age.name',
    descKey: 'tools.change-age.desc',
    input: 'single-image',
    params: [
      {
        key: 'targetAge',
        type: 'slider',
        labelKey: 'tools.change-age.param.targetAge',
        min: 5,
        max: 90,
        step: 1,
        default: 30,
      },
    ],
    cost: 5,
  },
  {
    id: 'change-gender',
    category: 'portrait',
    icon: 'Switch',
    nameKey: 'tools.change-gender.name',
    descKey: 'tools.change-gender.desc',
    input: 'single-image',
    params: [
      {
        key: 'targetGender',
        type: 'select',
        labelKey: 'tools.change-gender.param.targetGender',
        options: [
          { value: 'male', labelKey: 'tools.gender.male' },
          { value: 'female', labelKey: 'tools.gender.female' },
        ],
        default: 'male',
      },
    ],
    cost: 6,
  },
  {
    id: 'ai-avatar',
    category: 'portrait',
    icon: 'MagicStick',
    nameKey: 'tools.ai-avatar.name',
    descKey: 'tools.ai-avatar.desc',
    input: 'single-image',
    params: [
      {
        key: 'style',
        type: 'select',
        labelKey: 'tools.ai-avatar.param.style',
        options: [
          { value: 'comic', labelKey: 'tools.avatar-style.comic' },
          { value: 'anime', labelKey: 'tools.avatar-style.anime' },
          { value: 'oil', labelKey: 'tools.avatar-style.oil' },
          { value: 'sketch', labelKey: 'tools.avatar-style.sketch' },
        ],
        default: 'comic',
      },
    ],
    cost: 7,
    badge: 'new',
  },

  // ========== 图像处理 (8) ==========
  {
    id: 'image-watermark-remove',
    category: 'image',
    icon: 'MagicStick',
    nameKey: 'tools.image-watermark-remove.name',
    descKey: 'tools.image-watermark-remove.desc',
    input: 'single-image',
    params: [
      {
        key: 'mode',
        type: 'select',
        labelKey: 'tools.image-watermark-remove.param.mode',
        options: [
          { value: 'auto', labelKey: 'tools.watermark-mode.auto' },
          { value: 'manual', labelKey: 'tools.watermark-mode.manual' },
        ],
        default: 'auto',
      },
      {
        key: 'tolerance',
        type: 'slider',
        labelKey: 'tools.image-watermark-remove.param.tolerance',
        min: 10,
        max: 100,
        step: 5,
        default: 30,
      },
    ],
    cost: 4,
    badge: 'hot',
  },
  {
    id: 'image-beautify',
    category: 'image',
    icon: 'Sunny',
    nameKey: 'tools.image-beautify.name',
    descKey: 'tools.image-beautify.desc',
    input: 'single-image',
    params: [
      {
        key: 'strength',
        type: 'slider',
        labelKey: 'tools.image-beautify.param.strength',
        min: 0,
        max: 100,
        step: 5,
        default: 50,
      },
    ],
    cost: 3,
  },
  {
    id: 'image-crop',
    category: 'image',
    icon: 'Crop',
    nameKey: 'tools.image-crop.name',
    descKey: 'tools.image-crop.desc',
    input: 'single-image',
    params: [
      {
        key: 'ratio',
        type: 'select',
        labelKey: 'tools.image-crop.param.ratio',
        options: [
          { value: '1:1', labelKey: 'tools.ratio.1-1' },
          { value: '4:3', labelKey: 'tools.ratio.4-3' },
          { value: '16:9', labelKey: 'tools.ratio.16-9' },
          { value: '9:16', labelKey: 'tools.ratio.9-16' },
          { value: 'free', labelKey: 'tools.ratio.free' },
        ],
        default: '1:1',
      },
    ],
    cost: 1,
  },
  {
    id: 'style-transfer',
    category: 'image',
    icon: 'Brush',
    nameKey: 'tools.style-transfer.name',
    descKey: 'tools.style-transfer.desc',
    input: 'multi-image',
    maxImages: 2,
    params: [
      {
        key: 'strength',
        type: 'slider',
        labelKey: 'tools.style-transfer.param.strength',
        min: 0,
        max: 100,
        step: 5,
        default: 60,
      },
      {
        key: 'preserveColor',
        type: 'switch',
        labelKey: 'tools.style-transfer.param.preserveColor',
        default: true,
      },
    ],
    cost: 6,
    badge: 'hot',
  },
  {
    id: 'image-enhance',
    category: 'image',
    icon: 'Aim',
    nameKey: 'tools.image-enhance.name',
    descKey: 'tools.image-enhance.desc',
    input: 'single-image',
    params: [
      {
        key: 'scale',
        type: 'select',
        labelKey: 'tools.image-enhance.param.scale',
        options: [
          { value: '2', labelKey: 'tools.scale.2x' },
          { value: '4', labelKey: 'tools.scale.4x' },
        ],
        default: '2',
      },
      {
        key: 'denoise',
        type: 'slider',
        labelKey: 'tools.image-enhance.param.denoise',
        min: 0,
        max: 100,
        step: 5,
        default: 30,
      },
      {
        key: 'faceEnhance',
        type: 'switch',
        labelKey: 'tools.image-enhance.param.faceEnhance',
        default: false,
      },
    ],
    cost: 5,
  },
  {
    id: 'image-colorize',
    category: 'image',
    icon: 'Brush',
    nameKey: 'tools.image-colorize.name',
    descKey: 'tools.image-colorize.desc',
    input: 'single-image',
    params: [
      {
        key: 'palette',
        type: 'select',
        labelKey: 'tools.image-colorize.param.palette',
        options: [
          { value: 'auto', labelKey: 'tools.color-palette.auto' },
          { value: 'nature', labelKey: 'tools.color-palette.nature' },
          { value: 'portrait', labelKey: 'tools.color-palette.portrait' },
          { value: 'vintage', labelKey: 'tools.color-palette.vintage' },
        ],
        default: 'auto',
      },
      {
        key: 'saturation',
        type: 'slider',
        labelKey: 'tools.image-colorize.param.saturation',
        min: 50,
        max: 150,
        step: 5,
        default: 100,
      },
    ],
    cost: 4,
  },
  {
    id: 'background-replace',
    category: 'image',
    icon: 'PictureFilled',
    nameKey: 'tools.background-replace.name',
    descKey: 'tools.background-replace.desc',
    input: 'single-image',
    params: [
      {
        key: 'bgPrompt',
        type: 'textarea',
        labelKey: 'tools.background-replace.param.bgPrompt',
        rows: 2,
        maxlength: 200,
        placeholderKey: 'tools.background-replace.param.bgPrompt-ph',
        default: '',
      },
    ],
    cost: 6,
  },
  {
    id: 'image-compress',
    category: 'image',
    icon: 'ScaleToOriginal',
    nameKey: 'tools.image-compress.name',
    descKey: 'tools.image-compress.desc',
    input: 'single-image',
    params: [
      {
        key: 'quality',
        type: 'slider',
        labelKey: 'tools.image-compress.param.quality',
        min: 10,
        max: 100,
        step: 5,
        default: 70,
      },
    ],
    cost: 1,
  },

  // ========== 音频处理 (3) ==========
  {
    id: 'vocal-extract',
    category: 'audio',
    icon: 'Microphone',
    nameKey: 'tools.vocal-extract.name',
    descKey: 'tools.vocal-extract.desc',
    input: 'audio',
    params: [
      {
        key: 'mode',
        type: 'select',
        labelKey: 'tools.vocal-extract.param.mode',
        options: [
          { value: 'vocal', labelKey: 'tools.vocal-mode.vocal' },
          { value: 'accompaniment', labelKey: 'tools.vocal-mode.accompaniment' },
          { value: 'both', labelKey: 'tools.vocal-mode.both' },
        ],
        default: 'vocal',
      },
    ],
    cost: 6,
    badge: 'hot',
  },
  {
    id: 'audio-denoise',
    category: 'audio',
    icon: 'Headset',
    nameKey: 'tools.audio-denoise.name',
    descKey: 'tools.audio-denoise.desc',
    input: 'audio',
    params: [
      {
        key: 'strength',
        type: 'slider',
        labelKey: 'tools.audio-denoise.param.strength',
        min: 0,
        max: 100,
        step: 10,
        default: 60,
      },
    ],
    cost: 4,
  },
  {
    id: 'audio-speed',
    category: 'audio',
    icon: 'VideoPlay',
    nameKey: 'tools.audio-speed.name',
    descKey: 'tools.audio-speed.desc',
    input: 'audio',
    params: [
      {
        key: 'speed',
        type: 'slider',
        labelKey: 'tools.audio-speed.param.speed',
        min: 0.5,
        max: 2,
        step: 0.1,
        default: 1,
      },
      {
        key: 'pitch',
        type: 'slider',
        labelKey: 'tools.audio-speed.param.pitch',
        min: -12,
        max: 12,
        step: 1,
        default: 0,
      },
    ],
    cost: 3,
  },

  // ========== 视频处理 (4) ==========
  {
    id: 'video-watermark-remove',
    category: 'video',
    icon: 'VideoCamera',
    nameKey: 'tools.video-watermark-remove.name',
    descKey: 'tools.video-watermark-remove.desc',
    input: 'video',
    params: [
      {
        key: 'mode',
        type: 'select',
        labelKey: 'tools.video-watermark-remove.param.mode',
        options: [
          { value: 'auto', labelKey: 'tools.watermark-mode.auto' },
          { value: 'manual', labelKey: 'tools.watermark-mode.manual' },
        ],
        default: 'auto',
      },
      {
        key: 'tolerance',
        type: 'slider',
        labelKey: 'tools.video-watermark-remove.param.tolerance',
        min: 10,
        max: 100,
        step: 5,
        default: 30,
      },
    ],
    cost: 10,
  },
  {
    id: 'video-compress',
    category: 'video',
    icon: 'Files',
    nameKey: 'tools.video-compress.name',
    descKey: 'tools.video-compress.desc',
    input: 'video',
    params: [
      {
        key: 'quality',
        type: 'select',
        labelKey: 'tools.video-compress.param.quality',
        options: [
          { value: 'low', labelKey: 'tools.quality.low' },
          { value: 'medium', labelKey: 'tools.quality.medium' },
          { value: 'high', labelKey: 'tools.quality.high' },
        ],
        default: 'medium',
      },
      {
        key: 'resolution',
        type: 'select',
        labelKey: 'tools.video-compress.param.resolution',
        options: [
          { value: 'orig', labelKey: 'tools.video-resolution.orig' },
          { value: '1080', labelKey: 'tools.video-resolution.1080' },
          { value: '720', labelKey: 'tools.video-resolution.720' },
        ],
        default: 'orig',
      },
      {
        key: 'bitrate',
        type: 'slider',
        labelKey: 'tools.video-compress.param.bitrate',
        min: 500,
        max: 5000,
        step: 100,
        default: 2000,
      },
    ],
    cost: 5,
  },
  {
    id: 'video-to-gif',
    category: 'video',
    icon: 'Film',
    nameKey: 'tools.video-to-gif.name',
    descKey: 'tools.video-to-gif.desc',
    input: 'video',
    params: [
      {
        key: 'fps',
        type: 'slider',
        labelKey: 'tools.video-to-gif.param.fps',
        min: 5,
        max: 30,
        step: 5,
        default: 15,
      },
      {
        key: 'size',
        type: 'select',
        labelKey: 'tools.video-to-gif.param.size',
        options: [
          { value: '480', labelKey: 'tools.size.480' },
          { value: '720', labelKey: 'tools.size.720' },
          { value: '1080', labelKey: 'tools.size.1080' },
        ],
        default: '480',
      },
    ],
    cost: 4,
    badge: 'new',
  },
  {
    id: 'video-subtitle-extract',
    category: 'video',
    icon: 'Document',
    nameKey: 'tools.video-subtitle-extract.name',
    descKey: 'tools.video-subtitle-extract.desc',
    input: 'video',
    params: [
      {
        key: 'lang',
        type: 'select',
        labelKey: 'tools.video-subtitle-extract.param.lang',
        options: [
          { value: 'zh', labelKey: 'tools.lang.zh' },
          { value: 'en', labelKey: 'tools.lang.en' },
          { value: 'auto', labelKey: 'tools.lang.auto' },
        ],
        default: 'auto',
      },
    ],
    cost: 6,
    badge: 'new',
  },
]

/** 按 id 查工具元数据 */
export function findTool(id: string): ToolMeta | undefined {
  return TOOL_LIST.find(t => t.id === id)
}

const BADGE_WEIGHT: Record<NonNullable<ToolMeta['badge']>, number> = {
  hot: 0,
  new: 1,
}

/** 同类工具内排序: 优先 hot → new → 无角标, 再按 cost 升序 */
function sortTools(list: ToolMeta[]): ToolMeta[] {
  return [...list].sort((a, b) => {
    const wa = a.badge ? BADGE_WEIGHT[a.badge] : 2
    const wb = b.badge ? BADGE_WEIGHT[b.badge] : 2
    if (wa !== wb) return wa - wb
    return a.cost - b.cost
  })
}

/** 按类别分组, 同类内排序 (hot → new → 无角标, 再按 cost 升序) */
export function groupToolsByCategory(): Array<{ category: ToolMeta['category']; tools: ToolMeta[] }> {
  const order: ToolMeta['category'][] = ['portrait', 'image', 'audio', 'video']
  return order.map(category => ({
    category,
    tools: sortTools(TOOL_LIST.filter(t => t.category === category)),
  }))
}
