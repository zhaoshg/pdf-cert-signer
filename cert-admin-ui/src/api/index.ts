import axios from 'axios'
import { message } from 'ant-design-vue'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  res => {
    if (res.data.code !== 0) {
      message.error(res.data.message || '请求失败')
      return Promise.reject(new Error(res.data.message))
    }
    return res.data
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    message.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default api
