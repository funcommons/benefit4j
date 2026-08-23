// 旧版 Mock 类型（保留用于兼容性过渡）
// 新代码请使用 @/api/types 中的类型

// User Types
export interface MockUser {
  id: string
  name: string
  avatar: string
  department: string
  role: string
  credits: number
}

// Work Types
export type WorkType = 'video' | 'image'
export type WorkStatus = 'processing' | 'success' | 'failed' | 'discarded'

export interface MockWork {
  id: string
  type: WorkType
  status: WorkStatus
  thumbnail: string
  url?: string
  prompt: string
  modelName: string
  modelId?: string
  aspectRatio: string
  resolution: string
  duration?: number
  tags: string[]
  remark?: string
  isFavorite?: boolean
  isShared?: boolean
  shareTitle?: string
  shareId?: string  // 分享记录ID，用于社区收藏
  referenceMode?: 'frames' | 'subject'
  referenceImageUrls?: string[]
  createdAt: string
  author?: {
    id: string
    name: string
    avatar: string
  }
  likes?: number
  favorites?: number
  comments?: number
  isLiked?: boolean
  cost?: number
  generationTime?: number
  finishedAt?: string
  externalResult?: any
  errorMessage?: string  // 失败原因
}

// Model Types
export type ModelType = 'video' | 'image'

export interface MockModel {
  id: string
  name: string
  type: ModelType
  cost: number
  description: string
  isHot?: boolean
  isNew?: boolean
}

// Comment Types
export interface MockComment {
  id: string
  userId: string
  userName: string
  userAvatar: string
  content: string
  createdAt: string
  likes: number
  isLiked?: boolean
  replies?: MockComment[]
}

// Inspiration/Community Types
export interface MockInspirationWork extends MockWork {
  description: string
  isPublic: true
}

// Filter Types
export type WorkStatusFilter = 'all' | WorkStatus
export type InspirationFilter = 'all' | WorkType | 'popular'

// Config Types
export interface VideoConfig {
  aspectRatio: string
  resolution: string
  duration: number
}

export interface ImageConfig {
  aspectRatio: string
  resolution: string
}

// Upload Types
export type UploadType = 'start' | 'end' | 'subject' | 'reference'
export type ReferenceMode = 'subject' | 'frames'

export interface UploadFile {
  id: string
  url: string
  name: string
  type: UploadType
}

// Credit History Types
export interface CreditHistory {
  id: string
  type: 'earn' | 'spend' | 'recharge'
  amount: number
  description: string
  createdAt: string
}

// Material Types
export interface MockMaterial {
  id: string
  name: string
  url: string
  type: 'image' | 'video'
  category: string
  tags: string[]
  createdAt: string
}

// 从 API 类型重新导出，方便使用
export * from '@/api/types'