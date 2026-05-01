import axios from 'axios';
import { message } from 'ant-design-vue';

type BaseResponse<T> = {
  code: number;
  data: T;
  message: string;
};

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
});

http.interceptors.request.use((config) => {
  const raw = localStorage.getItem('dong-rag-admin-auth');
  if (raw) {
    try {
      const auth = JSON.parse(raw) as { token?: string };
      if (auth.token) {
        config.headers.Authorization = auth.token;
      }
    } catch {
      // ignore
    }
  }
  return config;
});

export async function request<T>(config: Parameters<typeof http.request>[0]): Promise<T> {
  try {
    const res = await http.request<BaseResponse<T>>(config);
    if (res.data.code !== 0) {
      const msg = res.data.message || '请求失败';
      message.error(msg);
      throw new Error(msg);
    }
    return res.data.data;
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '网络异常';
    message.error(msg);
    throw new Error(msg);
  }
}
