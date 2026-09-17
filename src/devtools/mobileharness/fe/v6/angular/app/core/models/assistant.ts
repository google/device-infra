/**
 * @fileoverview Domain models and RPC interfaces for OmniLab Assistant.
 * Corresponds to devtools/mobileharness/fe/v6/service/proto/assistant/assistant_service.proto.
 */

/** Operational security and identity modes for an assistant conversation. */
export enum ConversationMode {
  UNSPECIFIED = 'CONVERSATION_MODE_UNSPECIFIED',
  /** Mode 1: Personal user co-pilot forwarding caller end-user credentials (EUC). */
  GENERAL_USER = 'CONVERSATION_MODE_GENERAL_USER',
  /** Mode 2: Team operational co-pilot running under service identity. */
  TEAM_ASSISTANT = 'CONVERSATION_MODE_TEAM_ASSISTANT',
}

/** Lifecycle execution status of an assistant turn. */
export enum TurnStatus {
  UNSPECIFIED = 'TURN_STATUS_UNSPECIFIED',
  /** The turn is queued and waiting for worker execution to begin. */
  QUEUED = 'TURN_STATUS_QUEUED',
  /** The turn is actively executing reasoning steps and tools. */
  RUNNING = 'TURN_STATUS_RUNNING',
  /** The turn has completed successfully. */
  COMPLETED = 'TURN_STATUS_COMPLETED',
  /** The turn encountered an unrecoverable error during execution. */
  FAILED = 'TURN_STATUS_FAILED',
  /** The turn was explicitly cancelled by the user. */
  CANCELLED = 'TURN_STATUS_CANCELLED',
}

/** Incremental token chunk representing internal model reasoning. */
export declare interface ThoughtDelta {
  readonly text: string;
}

/** Notification of a tool invocation triggered by the model. */
export declare interface ToolCallDelta {
  readonly callId: string;
  readonly toolName: string;
  readonly argumentsJson: string;
}

/** Execution output returned from a tool invocation. */
export declare interface ToolResultDelta {
  readonly callId: string;
  readonly toolName: string;
  readonly success: boolean;
  readonly resultJson?: string;
  readonly errorMessage?: string;
}

/** Record of a tool execution performed during a turn. */
export declare interface ToolExecution {
  readonly callId: string;
  readonly toolName: string;
  readonly argumentsJson: string;
  readonly success: boolean;
  readonly resultJson?: string;
  readonly errorMessage?: string;
}

/** Incremental token chunk representing the final assistant response. */
export declare interface MessageDelta {
  readonly text: string;
}

/** Structured navigation reference pointing to an FE v6 console route. */
export declare interface OutboundLink {
  readonly title: string;
  readonly url: string;
}

/** Decision status for a human-in-the-loop mutation confirmation request. */
export enum ActionDecisionStatus {
  PENDING = 'ACTION_DECISION_STATUS_PENDING',
  APPROVED = 'ACTION_DECISION_STATUS_APPROVED',
  REJECTED = 'ACTION_DECISION_STATUS_REJECTED',
}

/** Risk classification for a state-changing operational action. */
export enum ActionRiskLevel {
  LOW = 'ACTION_RISK_LEVEL_LOW',
  MEDIUM = 'ACTION_RISK_LEVEL_MEDIUM',
  HIGH = 'ACTION_RISK_LEVEL_HIGH',
}

/** Human-in-the-loop authorization request for a state-changing mutation. */
export declare interface ActionConfirmationRequest {
  readonly actionId: string;
  readonly toolName: string;
  readonly targetEntity: string;
  readonly summary: string;
  readonly riskLevel: ActionRiskLevel;
  readonly status: ActionDecisionStatus;
  readonly parametersJson?: string;
  readonly resolvedBy?: string;
  readonly resolvedTime?: string;
}

/** Status transition notification for an active turn. */
export declare interface TurnStatusUpdate {
  readonly status: TurnStatus;
  readonly errorMessage?: string;
}

/** A single incremental event emitted during turn execution. */
export declare interface AssistantEvent {
  readonly seq: number | string;
  readonly timestamp?: string;
  readonly thoughtDelta?: ThoughtDelta;
  readonly toolCall?: ToolCallDelta;
  readonly toolResult?: ToolResultDelta;
  readonly messageDelta?: MessageDelta;
  readonly outboundLink?: OutboundLink;
  readonly actionConfirmation?: ActionConfirmationRequest;
  readonly statusUpdate?: TurnStatusUpdate;
}

/** Record of a single user prompt and corresponding assistant execution. */
export declare interface Turn {
  readonly turnId: string;
  readonly userPrompt: string;
  readonly status: TurnStatus;
  readonly startTime?: string;
  readonly endTime?: string;
  readonly thoughts?: string;
  readonly responseMarkdown?: string;
  readonly toolExecutions?: readonly ToolExecution[];
  readonly outboundLinks?: readonly OutboundLink[];
  readonly actionConfirmations?: readonly ActionConfirmationRequest[];
  readonly errorMessage?: string;
}

/** Complete conversation record containing metadata and historical turns. */
export declare interface Conversation {
  readonly conversationId: string;
  readonly title: string;
  readonly mode: ConversationMode;
  readonly userLdap: string;
  readonly createTime?: string;
  readonly updateTime?: string;
  readonly turns?: readonly Turn[];
}

/** Request to create a new conversation. */
export declare interface CreateConversationRequest {
  readonly title?: string;
  readonly mode?: ConversationMode;
}

/** Response containing the newly created conversation. */
export declare interface CreateConversationResponse {
  readonly conversation: Conversation;
}

/** Request to submit a user message to an active conversation. */
export declare interface SendMessageRequest {
  readonly conversationId: string;
  readonly userPrompt: string;
}

/** Response returned immediately after submitting a user message. */
export declare interface SendMessageResponse {
  readonly conversationId: string;
  readonly turnId: string;
  readonly status: TurnStatus;
}

/** Request to poll for incremental events from a turn. */
export declare interface PollEventsRequest {
  readonly conversationId: string;
  readonly turnId: string;
  readonly lastEventSeq: number;
  readonly timeoutSeconds?: number;
}

/** Response containing incremental delta events for a turn. */
export declare interface PollEventsResponse {
  readonly conversationId: string;
  readonly turnId: string;
  readonly turnStatus: TurnStatus;
  readonly events: readonly AssistantEvent[];
  readonly nextEventSeq: number;
  readonly isTurnDone: boolean;
}

/** Request to retrieve a conversation by identifier. */
export declare interface GetConversationRequest {
  readonly conversationId: string;
}

/** Response containing the requested conversation record. */
export declare interface GetConversationResponse {
  readonly conversation: Conversation;
}

/** Request to list conversations for the caller. */
export declare interface ListConversationsRequest {
  readonly pageSize?: number;
  readonly pageToken?: string;
}

/** Paginated response containing conversations. */
export declare interface ListConversationsResponse {
  readonly conversations: readonly Conversation[];
  readonly nextPageToken?: string;
}

/** Request to cancel an in-flight turn execution. */
export declare interface CancelTurnRequest {
  readonly conversationId: string;
  readonly turnId: string;
}

/** Response acknowledging the turn cancellation request. */
export declare interface CancelTurnResponse {
  readonly success: boolean;
  readonly message?: string;
}

/** Request to submit an operator decision (approve/reject) for a pending action. */
export declare interface SubmitActionDecisionRequest {
  readonly conversationId: string;
  readonly turnId: string;
  readonly actionId: string;
  readonly approved: boolean;
  readonly reason?: string;
}

/** Response returned after submitting an operator decision. */
export declare interface SubmitActionDecisionResponse {
  readonly actionConfirmation: ActionConfirmationRequest;
}
