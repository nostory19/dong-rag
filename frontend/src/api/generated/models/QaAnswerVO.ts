/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ChunkEvidenceVO } from './ChunkEvidenceVO';
export type QaAnswerVO = {
    answer?: string;
    evidenceEnough?: boolean;
    confidenceLevel?: string;
    confidenceScore?: number;
    evidences?: Array<ChunkEvidenceVO>;
};

