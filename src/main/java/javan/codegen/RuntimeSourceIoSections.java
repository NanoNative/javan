package javan.codegen;

final class RuntimeSourceIoSections {
    private static final String SOURCE_HTTP = """
        static char* javan_http_copy_range(const char* value, unsigned long length) {
            char* result = javan_string_alloc(length + 1UL);
            if (length > 0) {
                memcpy(result, value, length);
            }
            result[length] = '\\0';
            return result;
        }

        static javan_uri_value* javan_uri_checked(void* value) {
            if (value == NULL) {
                javan_panic("null uri");
            }
            javan_uri_value* uri = (javan_uri_value*) value;
            if (uri->magic != JAVAN_URI_MAGIC) {
                javan_panic("unsupported uri object");
            }
            return uri;
        }

        static javan_http_client_value* javan_http_client_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http client");
            }
            javan_http_client_value* client = (javan_http_client_value*) value;
            if (client->magic != JAVAN_HTTP_CLIENT_MAGIC) {
                javan_panic("unsupported http client object");
            }
            return client;
        }

        static javan_http_request_builder_value* javan_http_request_builder_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http request builder");
            }
            javan_http_request_builder_value* builder = (javan_http_request_builder_value*) value;
            if (builder->magic != JAVAN_HTTP_REQUEST_BUILDER_MAGIC || builder->uri == NULL || builder->headers == NULL) {
                javan_panic("unsupported http request builder object");
            }
            return builder;
        }

        static javan_http_request_value* javan_http_request_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http request");
            }
            javan_http_request_value* request = (javan_http_request_value*) value;
            if (request->magic != JAVAN_HTTP_REQUEST_MAGIC || request->uri == NULL || request->headers == NULL) {
                javan_panic("unsupported http request object");
            }
            return request;
        }

        static javan_http_body_publisher_value* javan_http_body_publisher_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http body publisher");
            }
            javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) value;
            if (publisher->magic != JAVAN_HTTP_BODY_PUBLISHER_MAGIC
                || (publisher->kind != JAVAN_HTTP_BODY_KIND_STRING && publisher->kind != JAVAN_HTTP_BODY_KIND_BYTE_ARRAY)
                || publisher->value == NULL) {
                javan_panic("unsupported http body publisher object");
            }
            return publisher;
        }

        static javan_http_body_handler_value* javan_http_body_handler_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http body handler");
            }
            javan_http_body_handler_value* handler = (javan_http_body_handler_value*) value;
            if (handler->magic != JAVAN_HTTP_BODY_HANDLER_MAGIC
                || (handler->kind != JAVAN_HTTP_BODY_KIND_STRING && handler->kind != JAVAN_HTTP_BODY_KIND_BYTE_ARRAY)) {
                javan_panic("unsupported http body handler object");
            }
            return handler;
        }

        static javan_http_response_value* javan_http_response_checked(void* value) {
            if (value == NULL) {
                javan_panic("null http response");
            }
            javan_http_response_value* response = (javan_http_response_value*) value;
            if (response->magic != JAVAN_HTTP_RESPONSE_MAGIC || response->body == NULL) {
                javan_panic("unsupported http response object");
            }
            return response;
        }

        static void javan_http_header_text_checked(const char* value, const char* kind) {
            if (value == NULL) {
                javan_panic(kind);
            }
            for (const char* cursor = value; cursor[0] != '\\0'; cursor++) {
                if (cursor[0] == '\\r' || cursor[0] == '\\n') {
                    javan_panic("http header contains a newline");
                }
            }
        }

        static void javan_http_parse_uri_text(const char* text, char** scheme_out, char** host_out, int* port_out, char** target_out) {
            if (text == NULL) {
                javan_panic("null uri text");
            }
            const char* prefix = "http://";
            unsigned long prefix_length = strlen(prefix);
            if (strncmp(text, prefix, prefix_length) != 0) {
                javan_panic("unsupported uri scheme");
            }
            const char* authority = text + prefix_length;
            if (authority[0] == '\\0') {
                javan_panic("uri host is missing");
            }
            const char* cursor = authority;
            while (cursor[0] != '\\0' && cursor[0] != '/' && cursor[0] != '?') {
                cursor++;
            }
            const char* host_end = cursor;
            int port = 80;
            const char* colon = NULL;
            for (const char* scan = authority; scan < host_end; scan++) {
                if (scan[0] == ':') {
                    colon = scan;
                }
            }
            if (colon != NULL) {
                host_end = colon;
                const char* port_text = colon + 1;
                if (port_text >= cursor) {
                    javan_panic("uri port is missing");
                }
                port = 0;
                while (port_text < cursor) {
                    if (port_text[0] < '0' || port_text[0] > '9') {
                        javan_panic("uri port is invalid");
                    }
                    port = port * 10 + (port_text[0] - '0');
                    port_text++;
                }
                if (port < 0 || port > 65535) {
                    javan_panic("uri port is out of range");
                }
            }
            if (host_end <= authority) {
                javan_panic("uri host is missing");
            }
            const char* target = cursor;
            if (target[0] == '\\0') {
                target = "/";
            } else if (target[0] == '?') {
                unsigned long query_length = strlen(target);
                char* with_slash = javan_string_alloc(query_length + 2UL);
                with_slash[0] = '/';
                memcpy(with_slash + 1UL, target, query_length + 1UL);
                *scheme_out = javan_http_copy_range(prefix, prefix_length - 3UL);
                *host_out = javan_http_copy_range(authority, (unsigned long) (host_end - authority));
                *target_out = with_slash;
                *port_out = port;
                return;
            }
            *scheme_out = javan_http_copy_range(prefix, prefix_length - 3UL);
            *host_out = javan_http_copy_range(authority, (unsigned long) (host_end - authority));
            *target_out = javan_http_copy_range(target, strlen(target));
            *port_out = port;
        }

        void* javan_uri_create(void* value) {
            char* scheme = NULL;
            char* host = NULL;
            char* target = NULL;
            int port = 80;
            void** javan_uri_parse_roots[] = {
                (void**) &scheme,
                (void**) &host,
                (void**) &target
            };
            javan_root_frame_push(javan_uri_parse_roots, 3);
            javan_http_parse_uri_text((const char*) value, &scheme, &host, &port, &target);
            javan_uri_value* uri = (javan_uri_value*) javan_alloc(sizeof(javan_uri_value));
            void* uri_root = (void*) uri;
            void** javan_uri_owner_roots[] = {
                (void**) &scheme,
                (void**) &host,
                (void**) &target,
                (void**) &uri_root
            };
            javan_root_frame_push(javan_uri_owner_roots, 4);
            uri = (javan_uri_value*) uri_root;
            uri->magic = JAVAN_URI_MAGIC;
            uri->port = port;
            uri->reserved0 = 0;
            uri->reserved1 = 0;
            uri->scheme = scheme;
            uri->host = host;
            uri->target = target;
            javan_update_runtime_allocation_kind(uri_root, JAVAN_RUNTIME_KIND_URI);
            javan_root_frame_pop(javan_uri_owner_roots);
            javan_root_frame_pop(javan_uri_parse_roots);
            return uri;
        }

        void* javan_http_client_new(void) {
            javan_http_client_value* client = (javan_http_client_value*) javan_alloc(sizeof(javan_http_client_value));
            client->magic = JAVAN_HTTP_CLIENT_MAGIC;
            client->reserved0 = 0;
            client->reserved1 = 0;
            client->reserved2 = 0;
            javan_update_runtime_allocation_kind((void*) client, JAVAN_RUNTIME_KIND_HTTP_CLIENT);
            return client;
        }

        void* javan_http_request_builder_new(void* uri_value) {
            javan_uri_value* uri = javan_uri_checked(uri_value);
            void* uri_root = (void*) uri;
            javan_http_request_builder_value* builder = (javan_http_request_builder_value*) javan_alloc(sizeof(javan_http_request_builder_value));
            void* builder_root = (void*) builder;
            void** javan_http_builder_setup_roots[] = {
                (void**) &uri_root,
                (void**) &builder_root
            };
            javan_root_frame_push(javan_http_builder_setup_roots, 2);
            void* headers_root = javan_list_new_with_capacity(0, 0);
            void** javan_http_builder_roots[] = {
                (void**) &uri_root,
                (void**) &builder_root,
                (void**) &headers_root
            };
            javan_root_frame_push(javan_http_builder_roots, 3);
            builder = (javan_http_request_builder_value*) builder_root;
            builder->magic = JAVAN_HTTP_REQUEST_BUILDER_MAGIC;
            builder->method = JAVAN_HTTP_METHOD_GET;
            builder->reserved0 = 0;
            builder->reserved1 = 0;
            builder->uri = (javan_uri_value*) uri_root;
            builder->headers = (javan_object_list*) headers_root;
            builder->body = NULL;
            javan_update_runtime_allocation_kind(builder_root, JAVAN_RUNTIME_KIND_HTTP_REQUEST_BUILDER);
            javan_root_frame_pop(javan_http_builder_roots);
            javan_root_frame_pop(javan_http_builder_setup_roots);
            return builder_root;
        }

        void* javan_http_request_builder_get(void* value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            builder->method = JAVAN_HTTP_METHOD_GET;
            builder->body = NULL;
            return builder;
        }

        void* javan_http_request_builder_header(void* value, void* name_value, void* header_value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            javan_http_header_text_checked((const char*) name_value, "null http header name");
            javan_http_header_text_checked((const char*) header_value, "null http header value");
            void* builder_root = value;
            void* name_root = name_value;
            void* header_root = header_value;
            void** javan_http_header_roots[] = {
                (void**) &builder_root,
                (void**) &name_root,
                (void**) &header_root
            };
            javan_root_frame_push(javan_http_header_roots, 3);
            builder = (javan_http_request_builder_value*) builder_root;
            javan_list_append_raw(builder->headers, name_root);
            javan_list_append_raw(builder->headers, header_root);
            javan_root_frame_pop(javan_http_header_roots);
            return builder_root;
        }

        void* javan_http_body_publisher_string(void* value) {
            if (value == NULL) {
                javan_panic("null http request body");
            }
            javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) javan_alloc(sizeof(javan_http_body_publisher_value));
            void* publisher_root = (void*) publisher;
            void* value_root = value;
            void** javan_http_body_publisher_roots[] = {
                (void**) &publisher_root,
                (void**) &value_root
            };
            javan_root_frame_push(javan_http_body_publisher_roots, 2);
            publisher = (javan_http_body_publisher_value*) publisher_root;
            publisher->magic = JAVAN_HTTP_BODY_PUBLISHER_MAGIC;
            publisher->kind = JAVAN_HTTP_BODY_KIND_STRING;
            publisher->reserved0 = 0;
            publisher->reserved1 = 0;
            publisher->value = value_root;
            javan_update_runtime_allocation_kind(publisher_root, JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER);
            javan_root_frame_pop(javan_http_body_publisher_roots);
            return publisher_root;
        }

        void* javan_http_body_publisher_byte_array(void* value) {
            if (value == NULL) {
                javan_panic("null http request body");
            }
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            javan_http_body_publisher_value* publisher = (javan_http_body_publisher_value*) javan_alloc(sizeof(javan_http_body_publisher_value));
            void* publisher_root = (void*) publisher;
            void* value_root = value;
            void** javan_http_body_publisher_roots[] = {
                (void**) &publisher_root,
                (void**) &value_root
            };
            javan_root_frame_push(javan_http_body_publisher_roots, 2);
            publisher = (javan_http_body_publisher_value*) publisher_root;
            publisher->magic = JAVAN_HTTP_BODY_PUBLISHER_MAGIC;
            publisher->kind = JAVAN_HTTP_BODY_KIND_BYTE_ARRAY;
            publisher->reserved0 = 0;
            publisher->reserved1 = 0;
            publisher->value = value_root;
            javan_update_runtime_allocation_kind(publisher_root, JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER);
            javan_root_frame_pop(javan_http_body_publisher_roots);
            return publisher_root;
        }

        void* javan_http_request_builder_post(void* value, void* body_publisher_value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            builder->method = JAVAN_HTTP_METHOD_POST;
            builder->body = (void*) javan_http_body_publisher_checked(body_publisher_value);
            return builder;
        }

        void* javan_http_request_builder_put(void* value, void* body_publisher_value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            builder->method = JAVAN_HTTP_METHOD_PUT;
            builder->body = (void*) javan_http_body_publisher_checked(body_publisher_value);
            return builder;
        }

        void* javan_http_request_builder_build(void* value) {
            javan_http_request_builder_value* builder = javan_http_request_builder_checked(value);
            void* uri_root = (void*) builder->uri;
            void* headers_root = (void*) builder->headers;
            void* body_root = builder->body;
            void** javan_http_request_setup_roots[] = {
                (void**) &uri_root,
                (void**) &headers_root,
                (void**) &body_root
            };
            javan_root_frame_push(javan_http_request_setup_roots, 3);
            headers_root = javan_list_copy_of(headers_root);
            javan_http_request_value* request = (javan_http_request_value*) javan_alloc(sizeof(javan_http_request_value));
            void* request_root = (void*) request;
            void** javan_http_request_roots[] = {
                (void**) &uri_root,
                (void**) &headers_root,
                (void**) &body_root,
                (void**) &request_root
            };
            javan_root_frame_push(javan_http_request_roots, 4);
            request = (javan_http_request_value*) request_root;
            request->magic = JAVAN_HTTP_REQUEST_MAGIC;
            request->method = builder->method;
            request->reserved0 = 0;
            request->reserved1 = 0;
            request->uri = (javan_uri_value*) uri_root;
            request->headers = (javan_object_list*) headers_root;
            request->body = body_root;
            javan_update_runtime_allocation_kind(request_root, JAVAN_RUNTIME_KIND_HTTP_REQUEST);
            javan_root_frame_pop(javan_http_request_roots);
            javan_root_frame_pop(javan_http_request_setup_roots);
            return request_root;
        }

        void* javan_http_body_handler_string(void) {
            javan_http_body_handler_value* handler = (javan_http_body_handler_value*) javan_alloc(sizeof(javan_http_body_handler_value));
            handler->magic = JAVAN_HTTP_BODY_HANDLER_MAGIC;
            handler->kind = JAVAN_HTTP_BODY_KIND_STRING;
            handler->reserved0 = 0;
            handler->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) handler, JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER);
            return handler;
        }

        void* javan_http_body_handler_byte_array(void) {
            javan_http_body_handler_value* handler = (javan_http_body_handler_value*) javan_alloc(sizeof(javan_http_body_handler_value));
            handler->magic = JAVAN_HTTP_BODY_HANDLER_MAGIC;
            handler->kind = JAVAN_HTTP_BODY_KIND_BYTE_ARRAY;
            handler->reserved0 = 0;
            handler->reserved1 = 0;
            javan_update_runtime_allocation_kind((void*) handler, JAVAN_RUNTIME_KIND_HTTP_BODY_HANDLER);
            return handler;
        }

        static void javan_http_send_all(int fd, const char* bytes, unsigned long length) {
            unsigned long offset = 0;
            while (offset < length) {
                ssize_t sent = send(fd, bytes + offset, (size_t) (length - offset), 0);
                if (sent <= 0) {
                    javan_panic("http request write failed");
                }
                offset += (unsigned long) sent;
            }
        }

        static char* javan_http_read_all(int fd, unsigned long* length_out) {
            unsigned long capacity = 1024;
            unsigned long length = 0;
            char* buffer = (char*) malloc(capacity + 1UL);
            if (buffer == NULL) {
                javan_panic("out of memory");
            }
            while (1) {
                if (length == capacity) {
                    unsigned long next_capacity = capacity * 2UL;
                    char* next = (char*) realloc(buffer, next_capacity + 1UL);
                    if (next == NULL) {
                        free(buffer);
                        javan_panic("out of memory");
                    }
                    buffer = next;
                    capacity = next_capacity;
                }
                ssize_t received = recv(fd, buffer + length, (size_t) (capacity - length), 0);
                if (received < 0) {
                    free(buffer);
                    javan_panic("http response read failed");
                }
                if (received == 0) {
                    break;
                }
                length += (unsigned long) received;
            }
            buffer[length] = '\\0';
            *length_out = length;
            return buffer;
        }

        static int javan_http_parse_status_code(const char* response, unsigned long length, const char** body_start_out) {
            if (length < 12UL || strncmp(response, "HTTP/1.", 7) != 0) {
                javan_panic("invalid http response");
            }
            const char* status = strchr(response, ' ');
            if (status == NULL || status[1] < '0' || status[1] > '9') {
                javan_panic("invalid http status");
            }
            int status_code = 0;
            status++;
            for (int index = 0; index < 3; index++) {
                if (status[index] < '0' || status[index] > '9') {
                    javan_panic("invalid http status");
                }
                status_code = status_code * 10 + (status[index] - '0');
            }
            const char* body = strstr(response, "\\r\\n\\r\\n");
            if (body != NULL) {
                *body_start_out = body + 4;
                return status_code;
            }
            body = strstr(response, "\\n\\n");
            if (body != NULL) {
                *body_start_out = body + 2;
                return status_code;
            }
            javan_panic("invalid http response");
            return 0;
        }

        static unsigned long javan_http_body_publisher_length(javan_http_body_publisher_value* publisher) {
            if (publisher == NULL) {
                return 0;
            }
            if (publisher->kind == JAVAN_HTTP_BODY_KIND_STRING) {
                return strlen((const char*) publisher->value);
            }
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(publisher->value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            return (unsigned long) bytes->length;
        }

        static const signed char* javan_http_body_publisher_bytes(javan_http_body_publisher_value* publisher) {
            if (publisher == NULL) {
                return NULL;
            }
            if (publisher->kind == JAVAN_HTTP_BODY_KIND_STRING) {
                return (const signed char*) publisher->value;
            }
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(publisher->value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            return bytes->values;
        }

        void* javan_http_client_send(void* client_value, void* request_value, void* body_handler_value) {
        #if defined(_WIN32)
            (void) client_value;
            (void) request_value;
            (void) body_handler_value;
            javan_socket_runtime_unsupported();
            return NULL;
        #else
            void** javan_http_send_roots[] = {
                (void**) &client_value,
                (void**) &request_value,
                (void**) &body_handler_value
            };
            javan_root_frame_push(javan_http_send_roots, 3);
            (void) javan_http_client_checked(client_value);
            javan_http_request_value* request = javan_http_request_checked(request_value);
            javan_http_body_handler_value* body_handler = javan_http_body_handler_checked(body_handler_value);
            if (request->method != JAVAN_HTTP_METHOD_GET
                && request->method != JAVAN_HTTP_METHOD_POST
                && request->method != JAVAN_HTTP_METHOD_PUT) {
                javan_panic("http method is not supported yet");
            }
            javan_uri_value* uri = javan_uri_checked((void*) request->uri);
            javan_http_body_publisher_value* body_publisher = request->body == NULL ? NULL : javan_http_body_publisher_checked(request->body);
            struct sockaddr_in address;
            javan_socket_host_checked(uri->host, &address, uri->port);
            int fd = socket(AF_INET, SOCK_STREAM, 0);
            if (fd < 0) {
                javan_panic("http socket open failed");
            }
            if (connect(fd, (struct sockaddr*) &address, sizeof(address)) != 0) {
                javan_socket_native_close(fd);
                javan_panic("http connect failed");
            }
            char port_suffix[32];
            port_suffix[0] = '\\0';
            if (uri->port != 80) {
                snprintf(port_suffix, sizeof(port_suffix), ":%d", uri->port);
            }
            const char* method_text = request->method == JAVAN_HTTP_METHOD_POST
                ? "POST"
                : (request->method == JAVAN_HTTP_METHOD_PUT ? "PUT" : "GET");
            unsigned long request_body_length = javan_http_body_publisher_length(body_publisher);
            unsigned long size = strlen(method_text) + 1UL
                + strlen(uri->target)
                + strlen(" HTTP/1.1\\r\\nHost: ")
                + strlen(uri->host)
                + strlen(port_suffix)
                + strlen("\\r\\nConnection: close\\r\\n");
            if (request->method == JAVAN_HTTP_METHOD_POST || request->method == JAVAN_HTTP_METHOD_PUT) {
                char content_length_buffer[32];
                snprintf(content_length_buffer, sizeof(content_length_buffer), "%lu", request_body_length);
                size += strlen("Content-Length: ") + strlen(content_length_buffer) + 2UL;
            }
            javan_object_list* headers = request->headers;
            for (int index = 0; index < headers->length; index += 2) {
                const char* name = (const char*) headers->values[index];
                const char* header_value = (const char*) headers->values[index + 1];
                javan_http_header_text_checked(name, "null http header name");
                javan_http_header_text_checked(header_value, "null http header value");
                size += strlen(name) + 2UL + strlen(header_value) + 2UL;
            }
            size += 2UL;
            char* request_text = (char*) malloc(size + 1UL);
            if (request_text == NULL) {
                javan_socket_native_close(fd);
                javan_root_frame_pop(javan_http_send_roots);
                javan_panic("out of memory");
            }
            int written = snprintf(
                request_text,
                size + 1UL,
                "%s %s HTTP/1.1\\r\\nHost: %s%s\\r\\nConnection: close\\r\\n",
                method_text,
                uri->target,
                uri->host,
                port_suffix
            );
            if (written < 0) {
                free(request_text);
                javan_socket_native_close(fd);
                javan_root_frame_pop(javan_http_send_roots);
                javan_panic("http request format failed");
            }
            unsigned long offset = (unsigned long) written;
            for (int index = 0; index < headers->length; index += 2) {
                written = snprintf(
                    request_text + offset,
                    size + 1UL - offset,
                    "%s: %s\\r\\n",
                    (const char*) headers->values[index],
                    (const char*) headers->values[index + 1]
                );
                if (written < 0) {
                    free(request_text);
                    javan_socket_native_close(fd);
                    javan_root_frame_pop(javan_http_send_roots);
                    javan_panic("http request header format failed");
                }
                offset += (unsigned long) written;
            }
            if (request->method == JAVAN_HTTP_METHOD_POST || request->method == JAVAN_HTTP_METHOD_PUT) {
                written = snprintf(request_text + offset, size + 1UL - offset, "Content-Length: %lu\\r\\n", request_body_length);
                if (written < 0) {
                    free(request_text);
                    javan_socket_native_close(fd);
                    javan_root_frame_pop(javan_http_send_roots);
                    javan_panic("http request header format failed");
                }
                offset += (unsigned long) written;
            }
            memcpy(request_text + offset, "\\r\\n", 3);
            offset += 2UL;
            javan_http_send_all(fd, request_text, offset);
            if (request_body_length > 0) {
                javan_http_send_all(fd, (const char*) javan_http_body_publisher_bytes(body_publisher), request_body_length);
            }
            free(request_text);
            unsigned long response_length = 0;
            char* response = javan_http_read_all(fd, &response_length);
            javan_socket_native_close(fd);
            const char* body_start = NULL;
            int status_code = javan_http_parse_status_code(response, response_length, &body_start);
            unsigned long response_body_length = response_length - (unsigned long) (body_start - response);
            void* body = NULL;
            if (body_handler->kind == JAVAN_HTTP_BODY_KIND_STRING) {
                body = (void*) javan_http_copy_range(body_start, response_body_length);
            } else {
                body = javan_byte_array_from((const signed char*) body_start, (int) response_body_length);
            }
            free(response);
            javan_http_response_value* http_response = (javan_http_response_value*) javan_alloc(sizeof(javan_http_response_value));
            void* response_root = (void*) http_response;
            void* body_root = (void*) body;
            void** javan_http_response_roots[] = {
                (void**) &body_root,
                (void**) &response_root
            };
            javan_root_frame_push(javan_http_response_roots, 2);
            http_response = (javan_http_response_value*) response_root;
            http_response->magic = JAVAN_HTTP_RESPONSE_MAGIC;
            http_response->status_code = status_code;
            http_response->reserved0 = 0;
            http_response->reserved1 = 0;
            http_response->body = body_root;
            javan_update_runtime_allocation_kind(response_root, JAVAN_RUNTIME_KIND_HTTP_RESPONSE);
            javan_root_frame_pop(javan_http_response_roots);
            javan_root_frame_pop(javan_http_send_roots);
            return response_root;
        #endif
        }

        int javan_http_response_status_code(void* response) {
            return javan_http_response_checked(response)->status_code;
        }

        void* javan_http_response_body(void* response) {
            return javan_http_response_checked(response)->body;
        }
        """;
    private static final String SOURCE_FILES = """
        int javan_files_exists(void* path, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            return javan_stat_path(javan_path_checked(path), no_follow, &info) == 0;
        }

        int javan_files_is_directory(void* path, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            return javan_stat_path(javan_path_checked(path), no_follow, &info) == 0 && S_ISDIR(info.st_mode);
        }

        int javan_files_is_regular_file(void* path, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            return javan_stat_path(javan_path_checked(path), no_follow, &info) == 0 && S_ISREG(info.st_mode);
        }

        int javan_files_is_executable(void* path) {
            return access(javan_path_checked(path), X_OK) == 0;
        }

        void* javan_files_copy(void* source, void* target, void* options) {
            javan_copy_options_checked(options);
            const char* source_path = javan_path_checked(source);
            const char* target_path = javan_path_checked(target);
            FILE* input = fopen(source_path, "rb");
            if (input == NULL) {
                javan_panic("file copy source open failed");
            }
            javan_native_resource_frame input_resource;
            javan_native_resource_push(&input_resource, input, javan_native_file_cleanup);
            FILE* output = fopen(target_path, "wb");
            if (output == NULL) {
                javan_native_resource_pop(&input_resource);
                fclose(input);
                javan_panic("file copy target open failed");
            }
            javan_native_resource_frame output_resource;
            javan_native_resource_push(&output_resource, output, javan_native_file_cleanup);
            char buffer[8192];
            size_t read = 0;
            while ((read = fread(buffer, 1, sizeof(buffer), input)) > 0) {
                if (fwrite(buffer, 1, read, output) != read) {
                    javan_native_resource_pop(&output_resource);
                    fclose(output);
                    javan_native_resource_pop(&input_resource);
                    fclose(input);
                    javan_panic("file copy write failed");
                }
            }
            if (ferror(input)) {
                javan_native_resource_pop(&output_resource);
                fclose(output);
                javan_native_resource_pop(&input_resource);
                fclose(input);
                javan_panic("file copy read failed");
            }
            javan_native_resource_pop(&output_resource);
            fclose(output);
            javan_native_resource_pop(&input_resource);
            fclose(input);
            return (void*) target_path;
        }

        static void javan_mkdir_if_needed(const char* path) {
            if (path[0] == '\\0') {
                return;
            }
            if (mkdir(path, 0777) == 0) {
                return;
            }
            if (errno == EEXIST) {
                struct stat info;
                if (stat(path, &info) == 0 && S_ISDIR(info.st_mode)) {
                    return;
                }
            }
            javan_panic("createDirectories failed");
        }

        void* javan_files_create_directories(void* path_value, void* attributes) {
            javan_empty_options_checked(attributes);
            const char* path = javan_path_checked(path_value);
            char* copy = (char*) javan_string_copy(path);
            for (char* cursor = copy + 1; *cursor != '\\0'; cursor++) {
                if (*cursor == '/') {
                    *cursor = '\\0';
                    javan_mkdir_if_needed(copy);
                    *cursor = '/';
                }
            }
            javan_mkdir_if_needed(copy);
            javan_free(copy);
            return path_value;
        }

        void* javan_files_read_string(void* path_value) {
            const char* path = javan_path_checked(path_value);
            FILE* file = fopen(path, "rb");
            if (file == NULL) {
                javan_panic("readString failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            if (fseek(file, 0, SEEK_END) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            long length = ftell(file);
            if (length < 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            if (fseek(file, 0, SEEK_SET) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            char* result = javan_string_alloc((unsigned long) length + 1);
            if (length > 0 && fread(result, 1, (unsigned long) length, file) != (unsigned long) length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readString failed");
            }
            result[length] = '\\0';
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return result;
        }

        void* javan_files_write_string(void* path_value, void* value, void* options) {
            javan_empty_options_checked(options);
            const char* path = javan_path_checked(path_value);
            const char* text = value == NULL ? "" : (const char*) value;
            FILE* file = fopen(path, "wb");
            if (file == NULL) {
                javan_panic("writeString failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            unsigned long length = strlen(text);
            if (length > 0 && fwrite(text, 1, length, file) != length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("writeString failed");
            }
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return path_value;
        }

        void* javan_files_write_bytes(void* path_value, void* bytes_value, void* options) {
            javan_empty_options_checked(options);
            const char* path = javan_path_checked(path_value);
            javan_byte_array* bytes = (javan_byte_array*) javan_array_checked(bytes_value);
            javan_array_kind_checked((javan_array_header*) bytes, JAVAN_ARRAY_KIND_BYTE);
            FILE* file = fopen(path, "wb");
            if (file == NULL) {
                javan_panic("write bytes failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            if (bytes->length > 0
                && fwrite(bytes->values, 1, (unsigned long) bytes->length, file) != (unsigned long) bytes->length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("write bytes failed");
            }
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return path_value;
        }

        void* javan_files_read_all_bytes(void* path_value) {
            const char* path = javan_path_checked(path_value);
            FILE* file = fopen(path, "rb");
            if (file == NULL) {
                javan_panic("readAllBytes failed");
            }
            javan_native_resource_frame file_resource;
            javan_native_resource_push(&file_resource, file, javan_native_file_cleanup);
            if (fseek(file, 0, SEEK_END) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            long length = ftell(file);
            if (length < 0 || length > INT_MAX) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            if (fseek(file, 0, SEEK_SET) != 0) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            void* result = javan_byte_array_new((int) length);
            javan_byte_array* array = (javan_byte_array*) result;
            if (length > 0 && fread(array->values, 1, (unsigned long) length, file) != (unsigned long) length) {
                javan_native_resource_pop(&file_resource);
                fclose(file);
                javan_panic("readAllBytes failed");
            }
            javan_native_resource_pop(&file_resource);
            fclose(file);
            return result;
        }

        int javan_files_delete_if_exists(void* path_value) {
            const char* path = javan_path_checked(path_value);
            if (remove(path) == 0) {
                return 1;
            }
            if (errno == ENOENT) {
                return 0;
            }
            javan_panic("deleteIfExists failed");
            return 0;
        }

        long long javan_files_size(void* path_value) {
            struct stat info;
            if (stat(javan_path_checked(path_value), &info) != 0) {
                javan_panic("Files.size failed");
            }
            return (long long) info.st_size;
        }

        static long long javan_file_time_modified_millis(const struct stat* info) {
        #if defined(__APPLE__) || defined(__MACH__)
            return ((long long) info->st_mtimespec.tv_sec * 1000LL) + ((long long) info->st_mtimespec.tv_nsec / 1000000LL);
        #elif defined(_WIN32)
            return ((long long) info->st_mtime * 1000LL);
        #else
            return ((long long) info->st_mtim.tv_sec * 1000LL) + ((long long) info->st_mtim.tv_nsec / 1000000LL);
        #endif
        }

        void* javan_files_get_last_modified_time(void* path_value, void* options) {
            int no_follow = javan_link_options_no_follow(options);
            struct stat info;
            if (javan_stat_path(javan_path_checked(path_value), no_follow, &info) != 0) {
                javan_panic("getLastModifiedTime failed");
            }
            return javan_file_time_from_millis(javan_file_time_modified_millis(&info));
        }

        static void javan_directory_stream_insert_sorted(javan_object_list* list, void* value) {
            const char* path = javan_path_checked(value);
            int index = 0;
            while (index < list->length && strcmp(javan_path_checked(list->values[index]), path) <= 0) {
                index++;
            }
            javan_list_ensure_capacity(list, list->length + 1);
            if (index < list->length) {
                memmove(list->values + index + 1, list->values + index, (unsigned long) (list->length - index) * sizeof(void*));
            }
            list->values[index] = value;
            list->length++;
        }

        void* javan_files_new_directory_stream(void* path_value) {
            void* source_root = path_value;
            void* result_root = NULL;
            void** javan_directory_result_roots[] = {
                (void**) &source_root,
                (void**) &result_root
            };
            javan_root_frame_push(javan_directory_result_roots, 2);
            const char* path = javan_path_checked(source_root);
            result_root = javan_list_new_with_capacity(0, 1);
            javan_object_list* result = (javan_object_list*) result_root;
            DIR* directory = opendir(path);
            if (directory == NULL) {
                javan_panic("newDirectoryStream failed");
            }
            javan_native_resource_frame directory_resource;
            javan_native_resource_push(&directory_resource, directory, javan_native_dir_cleanup);
            struct dirent* entry = readdir(directory);
            while (entry != NULL) {
                if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0) {
                    void* child = javan_path_resolve(source_root, (void*) entry->d_name);
                    void** javan_directory_child_roots[] = {
                        (void**) &child
                    };
                    javan_root_frame_push(javan_directory_child_roots, 1);
                    javan_directory_stream_insert_sorted(result, child);
                    javan_root_frame_pop(javan_directory_child_roots);
                }
                entry = readdir(directory);
            }
            javan_native_resource_pop(&directory_resource);
            if (closedir(directory) != 0) {
                javan_panic("newDirectoryStream close failed");
            }
            javan_root_frame_pop(javan_directory_result_roots);
            return result_root;
        }

        static javan_optional* javan_optional_checked(void* value) {
            if (value == NULL) {
                javan_panic("null optional");
            }
            javan_optional* optional = (javan_optional*) value;
            if (optional->magic != JAVAN_OPTIONAL_MAGIC) {
                javan_panic("unsupported optional object");
            }
            return optional;
        }

        static void* javan_optional_new(void* value, int present) {
            void* value_root = value;
            void** javan_optional_value_roots[] = {
                (void**) &value_root
            };
            javan_root_frame_push(javan_optional_value_roots, 1);
            javan_optional* optional = (javan_optional*) javan_alloc(sizeof(javan_optional));
            optional->magic = JAVAN_OPTIONAL_MAGIC;
            optional->present = present;
            optional->reserved0 = 0;
            optional->reserved1 = 0;
            optional->value = present == 0 ? NULL : value_root;
            javan_update_runtime_allocation_kind((void*) optional, JAVAN_RUNTIME_KIND_OPTIONAL);
            javan_root_frame_pop(javan_optional_value_roots);
            return optional;
        }

        void* javan_optional_empty(void) {
            return javan_optional_new(NULL, 0);
        }

        void* javan_optional_of(void* value) {
            if (value == NULL) {
                javan_panic("null optional value");
            }
            return javan_optional_new(value, 1);
        }

        void* javan_optional_of_nullable(void* value) {
            return value == NULL ? javan_optional_empty() : javan_optional_of(value);
        }

        int javan_optional_is_present(void* value) {
            return javan_optional_checked(value)->present != 0;
        }

        int javan_optional_is_empty(void* value) {
            return javan_optional_checked(value)->present == 0;
        }

        void* javan_optional_or_else(void* value, void* fallback) {
            javan_optional* optional = javan_optional_checked(value);
            return optional->present == 0 ? fallback : optional->value;
        }

        void* javan_optional_or_else_throw(void* value) {
            javan_optional* optional = javan_optional_checked(value);
            if (optional->present == 0) {
                javan_panic("optional is empty");
            }
            return optional->value;
        }

        void* javan_string_value_of_int(int value) {
            char buffer[32];
            snprintf(buffer, sizeof(buffer), "%d", value);
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_long(long long value) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "%lld", value);
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_float(float value) {
            char buffer[64];
            javan_format_real(buffer, sizeof(buffer), value, "%.9g");
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_double(double value) {
            char buffer[128];
            javan_format_real(buffer, sizeof(buffer), value, "%.17g");
            return javan_string_copy(buffer);
        }

        void* javan_string_value_of_bool(int value) {
            return javan_string_copy(value == 0 ? "false" : "true");
        }

        void* javan_string_value_of_char(int value) {
            char buffer[2];
            buffer[0] = (char) value;
            buffer[1] = '\\0';
            return javan_string_copy(buffer);
        }

        void* javan_string_concat(const char* recipe, int argc, const char** values) {
            if (recipe == NULL || argc < 0 || values == NULL) {
                javan_panic("invalid string concat");
            }
            void* javan_concat_values[argc > 0 ? argc : 1];
            void** javan_concat_roots[argc > 0 ? argc : 1];
            for (int index = 0; index < argc; index++) {
                javan_concat_values[index] = (void*) values[index];
                javan_concat_roots[index] = (void**) &javan_concat_values[index];
            }
            unsigned long length = 0;
            int arg = 0;
            for (const unsigned char* cursor = (const unsigned char*) recipe; *cursor != '\\0'; cursor++) {
                if (*cursor == 1) {
                    if (arg >= argc) {
                        javan_panic("invalid string concat argument");
                    }
                    length += strlen(javan_concat_values[arg] == NULL ? "null" : (const char*) javan_concat_values[arg]);
                    arg++;
                } else if (*cursor == 2) {
                    javan_panic("unsupported string concat constant");
                } else {
                    length++;
                }
            }
            if (arg != argc) {
                javan_panic("invalid string concat argument count");
            }
            if (argc > 0) {
                javan_root_frame_push(javan_concat_roots, argc);
            }
            char* result = javan_string_alloc(length + 1);
            char* out = result;
            arg = 0;
            for (const unsigned char* cursor = (const unsigned char*) recipe; *cursor != '\\0'; cursor++) {
                if (*cursor == 1) {
                    const char* value = javan_concat_values[arg] == NULL ? "null" : (const char*) javan_concat_values[arg];
                    unsigned long value_length = strlen(value);
                    memcpy(out, value, value_length);
                    out += value_length;
                    arg++;
                } else {
                    *out = (char) *cursor;
                    out++;
                }
            }
            *out = '\\0';
            if (argc > 0) {
                javan_root_frame_pop(javan_concat_roots);
            }
            return result;
        }

        char* javan_string_export(const char* value) {
            const char* source = value == NULL ? "" : value;
            unsigned long length = strlen(source);
            void* source_root = (void*) source;
            void** javan_string_export_roots[] = {
                (void**) &source_root
            };
            javan_root_frame_push(javan_string_export_roots, 1);
            char* result = (char*) javan_export_alloc(length + 1);
            memcpy(result, (const char*) source_root, length + 1);
            javan_root_frame_pop(javan_string_export_roots);
            return result;
        }

        int javan_lcmp(long long left, long long right) {
            if (left > right) {
                return 1;
            }
            if (left == right) {
                return 0;
            }
            return -1;
        }

        int javan_float_compare(float left, float right, int nan_value) {
            if (isnan(left) || isnan(right)) {
                return nan_value;
            }
            if (left > right) {
                return 1;
            }
            if (left == right) {
                return 0;
            }
            return -1;
        }

        int javan_double_compare(double left, double right, int nan_value) {
            if (isnan(left) || isnan(right)) {
                return nan_value;
            }
            if (left > right) {
                return 1;
            }
            if (left == right) {
                return 0;
            }
            return -1;
        }

        void javan_panic(const char* value) {
            const char* message = value == NULL ? "javan panic" : value;
            JavanSourceContext* context = javan_source_context_top;
            if (context != NULL) {
                javan_panic_at(
                    context->code,
                    context->summary,
                    context->class_name,
                    context->method,
                    context->file,
                    context->line,
                    context->bytecode_offset,
                    context->source_line,
                    context->why,
                    context->fix,
                    message
                );
                return;
            }
            javan_record_error(message);
            jmp_buf* target = javan_panic_target;
            if (target == NULL) {
                fputs(message, stderr);
                fputc('\\n', stderr);
            }
            javan_runtime_lock_reset_for_panic();
            javan_source_context_top = NULL;
            javan_native_resource_cleanup_all();
            javan_root_frame_cleanup();
            if (target != NULL) {
                javan_panic_target = NULL;
                longjmp(*target, 1);
            }
            exit(1);
        }

        void javan_panic_at(
            const char* code,
            const char* summary,
            const char* class_name,
            const char* method,
            const char* file,
            int line,
            int bytecode_offset,
            const char* source_line,
            const char* why,
            const char* fix,
            const char* detail
        ) {
            const char* safe_code = javan_safe_text(code, "JAVAN-RUNTIME-PANIC");
            const char* safe_summary = javan_safe_text(summary, "native runtime panic");
            const char* safe_class = javan_safe_text(class_name, "unknown");
            const char* safe_method = javan_safe_text(method, "unknown");
            const char* safe_file = javan_safe_text(file, "unknown source");
            const char* safe_source_line = source_line == NULL ? "" : source_line;
            const char* safe_why = javan_safe_text(why, "A generated runtime failure was reached.");
            const char* safe_fix = javan_safe_text(fix, "Inspect the reachable Java code that triggered this failure.");
            const char* safe_detail = javan_safe_text(detail, "javan panic");
            javan_record_error_at(
                safe_code,
                safe_summary,
                safe_class,
                safe_method,
                safe_file,
                line,
                bytecode_offset,
                safe_source_line,
                safe_why,
                safe_fix,
                safe_detail
            );
            jmp_buf* target = javan_panic_target;
            if (target == NULL) {
                fprintf(stderr, "[%s] %s\\n\\n", safe_code, safe_summary);
                fprintf(stderr, "Where:\\n");
                if (line >= 0) {
                    fprintf(stderr, "  %s.%s(%s:%d)\\n", safe_class, safe_method, safe_file, line);
                } else {
                    fprintf(stderr, "  %s.%s(%s)\\n", safe_class, safe_method, safe_file);
                }
                fprintf(stderr, "  bytecode offset: %d\\n\\n", bytecode_offset);
                javan_print_source_code(safe_source_line);
                fprintf(stderr, "Why:\\n");
                fprintf(stderr, "  %s\\n", safe_why);
                fprintf(stderr, "  detail: %s\\n\\n", safe_detail);
                fprintf(stderr, "Fix:\\n");
                fprintf(stderr, "  %s\\n", safe_fix);
                fflush(stderr);
            }
            javan_runtime_lock_reset_for_panic();
            javan_source_context_top = NULL;
            javan_native_resource_cleanup_all();
            javan_root_frame_cleanup();
            if (target != NULL) {
                javan_panic_target = NULL;
                longjmp(*target, 1);
            }
            exit(1);
        }
        """;

    private RuntimeSourceIoSections() {
    }

    static String http() {
        return SOURCE_HTTP;
    }

    static String files() {
        return SOURCE_FILES;
    }
}
