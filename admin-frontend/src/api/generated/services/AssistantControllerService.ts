/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AssistantChatRequest } from '../models/AssistantChatRequest';
import type { BaseResponseMapStringObject } from '../models/BaseResponseMapStringObject';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AssistantControllerService {
    /**
     * @param groupId
     * @returns BaseResponseMapStringObject OK
     * @throws ApiError
     */
    public static evaluateComplaint(
        groupId: number,
    ): CancelablePromise<BaseResponseMapStringObject> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/assistant/eval/complaint',
            query: {
                'groupId': groupId,
            },
        });
    }
    /**
     * @param requestBody
     * @returns string OK
     * @throws ApiError
     */
    public static chat(
        requestBody: AssistantChatRequest,
    ): CancelablePromise<Array<string>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/assistant/chat',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
