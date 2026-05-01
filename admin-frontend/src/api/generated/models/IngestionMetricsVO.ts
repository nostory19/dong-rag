/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type IngestionMetricsVO = {
    totalJobs?: number;
    successJobs?: number;
    failedJobs?: number;
    failureRate?: number;
    avgDurationSeconds?: number;
    successRateByFileType?: Record<string, number>;
    retryCountDistribution?: Record<string, number>;
};

