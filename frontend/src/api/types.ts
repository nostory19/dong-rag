export type BaseResponse<T> = {
  code: number;
  data: T;
  message: string;
};

export type LoginUserVO = {
  id: number;
  userCode: string;
  displayName: string;
  userRole: string;
  token: string;
};

export type GroupVO = {
  id: number;
  groupCode: string;
  groupName: string;
  status: string;
};
