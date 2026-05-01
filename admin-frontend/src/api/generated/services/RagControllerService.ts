/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponseBoolean } from '../models/BaseResponseBoolean';
import type { BaseResponseIngestionJobVO } from '../models/BaseResponseIngestionJobVO';
import type { BaseResponseIngestionMetricsVO } from '../models/BaseResponseIngestionMetricsVO';
import type { BaseResponseIngestionTaskVO } from '../models/BaseResponseIngestionTaskVO';
import type { BaseResponseListIngestionJobVO } from '../models/BaseResponseListIngestionJobVO';
import type { BaseResponseQaAnswerVO } from '../models/BaseResponseQaAnswerVO';
import type { IngestTextRequest } from '../models/IngestTextRequest';
import type { QaAskRequest } from '../models/QaAskRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class RagControllerService {
    /**
     * @param requestBody
     * @returns BaseResponseQaAnswerVO OK
     * @throws ApiError
     */
    public static ask(
        requestBody: QaAskRequest,
    ): CancelablePromise<BaseResponseQaAnswerVO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/rag/qa/ask',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns BaseResponseIngestionTaskVO OK
     * @throws ApiError
     */
    public static ingestText(
        requestBody: IngestTextRequest,
    ): CancelablePromise<BaseResponseIngestionTaskVO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/rag/ingest/text',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param jobId
     * @returns BaseResponseBoolean OK
     * @throws ApiError
     */
    public static retryJob(
        jobId: number,
    ): CancelablePromise<BaseResponseBoolean> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/rag/ingest/jobs/{jobId}/retry',
            path: {
                'jobId': jobId,
            },
        });
    }
    /**
     * @param groupId
     * @param requestBody
     * @returns BaseResponseIngestionTaskVO OK
     * @throws ApiError
     */
    public static ingestFile(
        groupId: number,
        requestBody?: {
            file: Blob;
        },
    ): CancelablePromise<BaseResponseIngestionTaskVO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/rag/ingest/file',
            query: {
                'groupId': groupId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param documentId
     * @returns BaseResponseBoolean OK
     * @throws ApiError
     */
    public static rebuildDocument(
        documentId: number,
    ): CancelablePromise<BaseResponseBoolean> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/rag/ingest/documents/{documentId}/rebuild',
            path: {
                'documentId': documentId,
            },
        });
    }
    /**
     * @param jobId
     * @returns BaseResponseIngestionTaskVO OK
     * @throws ApiError
     */
    public static getTaskStatus(
        jobId: number,
    ): CancelablePromise<BaseResponseIngestionTaskVO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/rag/ingest/task/{jobId}',
            path: {
                'jobId': jobId,
            },
        });
    }
    /**
     * @returns BaseResponseIngestionMetricsVO OK
     * @throws ApiError
     */
    public static ingestionMetrics(): CancelablePromise<BaseResponseIngestionMetricsVO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/rag/ingest/metrics',
        });
    }
    /**
     * @param limit
     * @returns BaseResponseListIngestionJobVO OK
     * @throws ApiError
     */
    public static listJobs(
        limit?: number,
    ): CancelablePromise<BaseResponseListIngestionJobVO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/rag/ingest/jobs',
            query: {
                'limit': limit,
            },
        });
    }
    /**
     * @param jobId
     * @returns BaseResponseIngestionJobVO OK
     * @throws ApiError
     */
    public static getJob(
        jobId: number,
    ): CancelablePromise<BaseResponseIngestionJobVO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/rag/ingest/jobs/{jobId}',
            path: {
                'jobId': jobId,
            },
        });
    }
}
