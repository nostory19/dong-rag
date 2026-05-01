/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseBoolean } from '../models/BaseResponseBoolean';
import type { BaseResponseListLoginUserVO } from '../models/BaseResponseListLoginUserVO';
import type { BaseResponseLoginUserVO } from '../models/BaseResponseLoginUserVO';
import type { BaseResponseLong } from '../models/BaseResponseLong';
import type { UserLoginRequest } from '../models/UserLoginRequest';
import type { UserRegisterRequest } from '../models/UserRegisterRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserControllerService {
    /**
     * @param requestBody
     * @returns BaseResponseLong OK
     * @throws ApiError
     */
    public static userRegister(
        requestBody: UserRegisterRequest,
    ): CancelablePromise<BaseResponseLong> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/user/register',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @returns BaseResponseBoolean OK
     * @throws ApiError
     */
    public static userLogout(): CancelablePromise<BaseResponseBoolean> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/user/logout',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseLoginUserVO OK
     * @throws ApiError
     */
    public static userLogin(
        requestBody: UserLoginRequest,
    ): CancelablePromise<BaseResponseLoginUserVO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/user/login',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @returns BaseResponseListLoginUserVO OK
     * @throws ApiError
     */
    public static listUsers(): CancelablePromise<BaseResponseListLoginUserVO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/user/list',
        });
    }
}
