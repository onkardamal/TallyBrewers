# SecureBank Authentication

## Vision

Build a modern passwordless authentication system for banking applications.

The project focuses on one thing:

> Building the most secure, simple, and production-ready authentication experience.

We are NOT building:

- Admin Console
- Risk Engine
- Authorization System
- Team Approval Workflow
- Security Dashboard
- Manual Demo Console

## Goals

- Passwordless Authentication
- Beautiful User Experience
- Banking-grade Security
- Production-ready Backend
- Clean Architecture
- Easy to Understand
- Fully Functional
- No Mock Data

## Authentication Flow

Registration

↓

Email Verification

↓

Passkey Registration

↓

Recovery Code Generation

↓

Login

↓

Email

↓

Passkey Authentication

↓

Secure Session

↓

Dashboard

## Recovery

If user loses the device

↓

Email Verification

↓

Recovery Code

↓

Register New Passkey

↓

Old Passkey Revoked

## Core Technologies

Frontend
- React
- TailwindCSS

Backend
- Spring Boot

Database
- PostgreSQL

Authentication
- WebAuthn (Passkeys)

Sessions
- JWT
- HttpOnly Cookies

Security
- Spring Security