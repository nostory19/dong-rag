/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseBoolean } from '../models/BaseResponseBoolean';
import type { BaseResponseListGroupVO } from '../models/BaseResponseListGroupVO';
import type { BaseResponseLong } from '../models/BaseResponseLong';
import type { CreateGroupRequest } from '../models/CreateGroupRequest';
import type { JoinGroupRequest } from '../models/JoinGroupRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class GroupControllerService {
    /**
     * @param requestBody
     * @returns BaseResponseBoolean OK
     * @throws ApiError
     */
    public static join(
        requestBody: JoinGroupRequest,
    ): CancelablePromise<BaseResponseBoolean> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/group/join',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseLong OK
     * @throws ApiError
     */
    public static create(
        requestBody: CreateGroupRequest,
    ): CancelablePromise<BaseResponseLong> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/group/create',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @returns BaseResponseListGroupVO OK
     * @throws ApiError
     */
    public static myGroups(): CancelablePromise<BaseResponseListGroupVO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/group/my/list',
        });
    }
}
