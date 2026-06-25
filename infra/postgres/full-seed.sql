--
-- PostgreSQL database dump
--

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

-- Started on 2026-04-21 00:19:51

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 228 (class 1259 OID 16614)
-- Name: citations; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.citations (
    citation_id character varying(64) NOT NULL,
    vehicle_id integer NOT NULL,
    space_id integer NOT NULL,
    zone_code character varying(10) NOT NULL,
    inspection_time timestamp without time zone NOT NULL,
    amount numeric(10,2) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT citations_amount_check CHECK ((amount > (0)::numeric))
);


ALTER TABLE public.citations OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 16441)
-- Name: parking_events; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.parking_events (
    event_id integer NOT NULL,
    vehicle_id integer NOT NULL,
    space_id integer NOT NULL,
    customer_id character varying(32),
    start_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    end_time timestamp without time zone,
    status character varying(20) NOT NULL,
    calculated_amount numeric(8,2) DEFAULT 0.00,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT parking_events_calculated_amount_check CHECK ((calculated_amount >= (0)::numeric)),
    CONSTRAINT parking_events_status_check CHECK (((status)::text = ANY ((ARRAY['STARTED'::character varying, 'STOPPED'::character varying])::text[])))
);


ALTER TABLE public.parking_events OWNER TO postgres;

CREATE INDEX IF NOT EXISTS idx_events_customer_active ON public.parking_events (customer_id, status) WHERE end_time IS NULL;

--
-- TOC entry 225 (class 1259 OID 16440)
-- Name: parking_events_event_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.parking_events_event_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.parking_events_event_id_seq OWNER TO postgres;

--
-- TOC entry 5093 (class 0 OID 0)
-- Dependencies: 225
-- Name: parking_events_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.parking_events_event_id_seq OWNED BY public.parking_events.event_id;


--
-- TOC entry 224 (class 1259 OID 16422)
-- Name: parking_spaces; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.parking_spaces (
    space_id integer NOT NULL,
    space_number character varying(20) NOT NULL,
    zone_id integer NOT NULL,
    street_name character varying(100),
    is_active boolean DEFAULT true NOT NULL
);


ALTER TABLE public.parking_spaces OWNER TO postgres;

--
-- TOC entry 223 (class 1259 OID 16421)
-- Name: parking_spaces_space_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.parking_spaces_space_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.parking_spaces_space_id_seq OWNER TO postgres;

--
-- TOC entry 5094 (class 0 OID 0)
-- Dependencies: 223
-- Name: parking_spaces_space_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.parking_spaces_space_id_seq OWNED BY public.parking_spaces.space_id;


--
-- TOC entry 222 (class 1259 OID 16406)
-- Name: parking_zones; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.parking_zones (
    zone_id integer NOT NULL,
    zone_code character varying(10) NOT NULL,
    zone_name character varying(100) NOT NULL,
    hourly_rate numeric(6,2) NOT NULL,
    max_parking_minutes integer NOT NULL,
    CONSTRAINT parking_zones_hourly_rate_check CHECK ((hourly_rate >= (0)::numeric)),
    CONSTRAINT parking_zones_max_parking_minutes_check CHECK ((max_parking_minutes > 0))
);


ALTER TABLE public.parking_zones OWNER TO postgres;

--
-- TOC entry 221 (class 1259 OID 16405)
-- Name: parking_zones_zone_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.parking_zones_zone_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.parking_zones_zone_id_seq OWNER TO postgres;

--
-- TOC entry 5095 (class 0 OID 0)
-- Dependencies: 221
-- Name: parking_zones_zone_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.parking_zones_zone_id_seq OWNED BY public.parking_zones.zone_id;


--
-- TOC entry 227 (class 1259 OID 16581)
-- Name: payment_transactions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.payment_transactions (
    transaction_id character varying(64) NOT NULL,
    event_id integer NOT NULL,
    vehicle_id integer NOT NULL,
    space_id integer NOT NULL,
    zone_code character varying(10) NOT NULL,
    start_time timestamp without time zone NOT NULL,
    stop_time timestamp without time zone NOT NULL,
    amount numeric(10,2) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT payment_transactions_amount_check CHECK ((amount >= (0)::numeric))
);


ALTER TABLE public.payment_transactions OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 16394)
-- Name: vehicles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.vehicles (
    vehicle_id integer NOT NULL,
    plate_number character varying(20) NOT NULL,
    owner_name character varying(100) NOT NULL
);


ALTER TABLE public.vehicles OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 16393)
-- Name: vehicles_vehicle_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.vehicles_vehicle_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.vehicles_vehicle_id_seq OWNER TO postgres;

--
-- TOC entry 5096 (class 0 OID 0)
-- Dependencies: 219
-- Name: vehicles_vehicle_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.vehicles_vehicle_id_seq OWNED BY public.vehicles.vehicle_id;


--
-- TOC entry 4883 (class 2604 OID 16444)
-- Name: parking_events event_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_events ALTER COLUMN event_id SET DEFAULT nextval('public.parking_events_event_id_seq'::regclass);


--
-- TOC entry 4881 (class 2604 OID 16425)
-- Name: parking_spaces space_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_spaces ALTER COLUMN space_id SET DEFAULT nextval('public.parking_spaces_space_id_seq'::regclass);


--
-- TOC entry 4880 (class 2604 OID 16409)
-- Name: parking_zones zone_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_zones ALTER COLUMN zone_id SET DEFAULT nextval('public.parking_zones_zone_id_seq'::regclass);


--
-- TOC entry 4879 (class 2604 OID 16397)
-- Name: vehicles vehicle_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.vehicles ALTER COLUMN vehicle_id SET DEFAULT nextval('public.vehicles_vehicle_id_seq'::regclass);


--
-- TOC entry 5087 (class 0 OID 16614)
-- Dependencies: 228
-- Data for Name: citations; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.citations (citation_id, vehicle_id, space_id, zone_code, inspection_time, amount, created_at) FROM stdin;
70b1de77-caa5-4573-b1dd-61aa7411ac33	2	1	A1	2026-04-20 21:14:12.685906	250.00	2026-04-20 21:14:12.674618
8bdda1f6-a953-4ba3-833b-ab9ad262f690	2	1	A1	2026-04-20 21:14:21.76998	250.00	2026-04-20 21:14:21.765472
\.


--
-- TOC entry 5085 (class 0 OID 16441)
-- Dependencies: 226
-- Data for Name: parking_events; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.parking_events (event_id, vehicle_id, space_id, start_time, end_time, status, calculated_amount, created_at, updated_at) FROM stdin;
1	1	1	2026-03-13 20:30:58.576368	2026-03-13 22:30:58.576368	STOPPED	16.00	2026-03-13 23:30:58.576368	2026-03-13 23:30:58.576368
3	3	25	2026-03-13 18:30:58.576368	2026-03-13 20:30:58.576368	STOPPED	11.00	2026-03-13 23:30:58.576368	2026-03-13 23:30:58.576368
4	4	41	2026-03-13 23:00:58.576368	\N	STARTED	0.00	2026-03-13 23:30:58.576368	2026-03-13 23:30:58.576368
5	5	33	2026-03-13 23:31:34.283969	\N	STARTED	0.00	2026-03-13 23:31:34.283969	2026-03-13 23:31:34.283969
2	2	12	2026-03-13 21:30:58.576368	2026-03-13 23:31:43.037708	STOPPED	12.50	2026-03-13 23:30:58.576368	2026-03-13 23:31:43.037708
153	1	1	2026-04-20 16:16:21.681553	2026-04-20 18:38:47.03059	STOPPED	24.00	2026-04-20 16:16:21.681553	2026-04-20 18:38:47.03059
6	1	1	2026-03-17 14:41:31.794485	2026-03-17 14:48:50.057839	STOPPED	8.00	2026-03-17 14:41:31.794485	2026-03-17 14:41:31.794485
7	2	2	2026-03-19 14:23:30.301287	2026-03-19 14:23:32.192556	STOPPED	8.00	2026-03-19 14:23:30.301287	2026-03-19 14:23:30.301287
154	1	1	2026-04-20 18:39:55.234506	2026-04-20 18:43:21.76235	STOPPED	8.00	2026-04-20 18:39:55.234506	2026-04-20 18:43:21.76235
155	1	1	2026-04-20 18:44:24.143303	2026-04-20 18:44:25.785722	STOPPED	8.00	2026-04-20 18:44:24.143303	2026-04-20 18:44:25.785722
156	1	1	2026-04-20 18:44:45.199899	2026-04-20 18:44:46.653141	STOPPED	8.00	2026-04-20 18:44:45.199899	2026-04-20 18:44:46.653141
157	1	1	2026-04-20 18:46:18.292925	2026-04-20 18:46:19.515616	STOPPED	8.00	2026-04-20 18:46:18.292925	2026-04-20 18:46:19.515616
158	1	1	2026-04-20 18:54:28.489667	2026-04-20 18:54:31.031265	STOPPED	8.00	2026-04-20 18:54:28.489667	2026-04-20 18:54:31.031265
181	1	1	2026-04-20 21:05:50.69574	2026-04-20 21:05:55.394964	STOPPED	8.00	2026-04-20 21:05:50.69574	2026-04-20 21:05:55.394964
182	1	1	2026-04-20 21:06:01.618169	2026-04-20 22:48:35.877194	STOPPED	16.00	2026-04-20 21:06:01.618169	2026-04-20 22:48:35.832851
184	1	1	2026-04-20 22:48:35.832851	2026-04-20 22:48:37.072272	STOPPED	8.00	2026-04-20 22:48:35.832851	2026-04-20 22:48:37.066206
\.


--
-- TOC entry 5083 (class 0 OID 16422)
-- Dependencies: 224
-- Data for Name: parking_spaces; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.parking_spaces (space_id, space_number, zone_id, street_name, is_active) FROM stdin;
1	S001	1	Main St	t
2	S002	1	Main St	t
3	S003	1	Main St	t
4	S004	1	Main St	t
5	S005	1	Main St	t
6	S006	1	Main St	t
7	S007	1	Main St	t
8	S008	1	Main St	t
9	S009	1	Main St	t
10	S010	1	Main St	t
11	S011	2	Mall Road	t
12	S012	2	Mall Road	t
13	S013	2	Mall Road	t
14	S014	2	Mall Road	t
15	S015	2	Mall Road	t
16	S016	2	Mall Road	t
17	S017	2	Mall Road	t
18	S018	2	Mall Road	t
19	S019	2	Mall Road	t
20	S020	2	Mall Road	t
21	S021	3	North Ave	t
22	S022	3	North Ave	t
23	S023	3	North Ave	t
24	S024	3	North Ave	t
25	S025	3	North Ave	t
26	S026	3	North Ave	t
27	S027	3	North Ave	t
28	S028	3	North Ave	t
29	S029	3	North Ave	t
30	S030	3	North Ave	t
31	S031	4	South Ave	t
32	S032	4	South Ave	t
33	S033	4	South Ave	t
34	S034	4	South Ave	t
35	S035	4	South Ave	t
36	S036	4	South Ave	t
37	S037	4	South Ave	t
38	S038	4	South Ave	t
39	S039	4	South Ave	t
40	S040	4	South Ave	t
41	S041	5	Hospital Rd	t
42	S042	5	Hospital Rd	t
43	S043	5	Hospital Rd	t
44	S044	5	Hospital Rd	t
45	S045	5	Hospital Rd	t
46	S046	5	Hospital Rd	t
47	S047	5	Hospital Rd	t
48	S048	5	Hospital Rd	t
49	S049	5	Hospital Rd	t
50	S050	5	Hospital Rd	t
51	S051	6	Campus Blvd	t
52	S052	6	Campus Blvd	t
53	S053	6	Campus Blvd	t
54	S054	6	Campus Blvd	t
55	S055	6	Campus Blvd	t
56	S056	6	Campus Blvd	t
57	S057	6	Campus Blvd	t
58	S058	6	Campus Blvd	t
59	S059	6	Campus Blvd	t
60	S060	6	Campus Blvd	t
61	S061	7	Beach St	t
62	S062	7	Beach St	t
63	S063	7	Beach St	t
64	S064	7	Beach St	t
65	S065	7	Beach St	t
66	S066	7	Beach St	t
67	S067	7	Beach St	t
68	S068	7	Beach St	t
69	S069	7	Beach St	t
70	S070	7	Beach St	t
71	S071	8	Business Ave	t
72	S072	8	Business Ave	t
73	S073	8	Business Ave	t
74	S074	8	Business Ave	t
75	S075	8	Business Ave	t
76	S076	8	Business Ave	t
77	S077	8	Business Ave	t
78	S078	8	Business Ave	t
79	S079	8	Business Ave	t
80	S080	8	Business Ave	t
81	S081	9	Old City Rd	t
82	S082	9	Old City Rd	t
83	S083	9	Old City Rd	t
84	S084	9	Old City Rd	t
85	S085	9	Old City Rd	t
86	S086	9	Old City Rd	t
87	S087	9	Old City Rd	t
88	S088	9	Old City Rd	t
89	S089	9	Old City Rd	t
90	S090	9	Old City Rd	t
91	S091	10	Station Rd	t
92	S092	10	Station Rd	t
93	S093	10	Station Rd	t
94	S094	10	Station Rd	t
95	S095	10	Station Rd	t
96	S096	10	Station Rd	t
97	S097	10	Station Rd	t
98	S098	10	Station Rd	t
99	S099	10	Station Rd	t
100	S100	10	Station Rd	t
101	IMP-0	18	IMP7 Street	t
102	IMP-1	15	IMP4 Street	t
103	IMP-2	18	IMP7 Street	t
104	IMP-3	14	IMP3 Street	t
105	IMP-4	21	IMP10 Street	t
106	IMP-5	25	IMP14 Street	t
107	IMP-6	20	IMP9 Street	t
108	IMP-7	16	IMP5 Street	t
109	IMP-8	12	IMP1 Street	t
110	IMP-9	21	IMP10 Street	t
111	IMP-10	20	IMP9 Street	t
112	IMP-11	15	IMP4 Street	t
113	IMP-12	14	IMP3 Street	t
114	IMP-13	22	IMP11 Street	t
115	IMP-14	12	IMP1 Street	t
116	IMP-15	12	IMP1 Street	t
117	IMP-16	23	IMP12 Street	t
118	IMP-17	23	IMP12 Street	t
119	IMP-18	17	IMP6 Street	t
120	IMP-19	11	IMP0 Street	t
121	IMP-20	22	IMP11 Street	t
122	IMP-21	11	IMP0 Street	t
123	IMP-22	21	IMP10 Street	t
124	IMP-23	22	IMP11 Street	t
125	IMP-24	20	IMP9 Street	t
126	IMP-25	18	IMP7 Street	t
127	IMP-26	20	IMP9 Street	t
128	IMP-27	16	IMP5 Street	t
129	IMP-28	25	IMP14 Street	t
130	IMP-29	21	IMP10 Street	t
131	IMP-30	25	IMP14 Street	t
132	IMP-31	25	IMP14 Street	t
133	IMP-32	22	IMP11 Street	t
134	IMP-33	14	IMP3 Street	t
135	IMP-34	11	IMP0 Street	t
136	IMP-35	20	IMP9 Street	t
137	IMP-36	21	IMP10 Street	t
138	IMP-37	21	IMP10 Street	t
139	IMP-38	14	IMP3 Street	t
140	IMP-39	21	IMP10 Street	t
141	IMP-40	25	IMP14 Street	t
142	IMP-41	23	IMP12 Street	t
143	IMP-42	19	IMP8 Street	t
144	IMP-43	13	IMP2 Street	t
145	IMP-44	15	IMP4 Street	t
146	IMP-45	16	IMP5 Street	t
147	IMP-46	24	IMP13 Street	t
148	IMP-47	13	IMP2 Street	t
149	IMP-48	19	IMP8 Street	t
150	IMP-49	13	IMP2 Street	t
151	IMP-50	22	IMP11 Street	t
152	IMP-51	15	IMP4 Street	t
153	IMP-52	11	IMP0 Street	t
154	IMP-53	22	IMP11 Street	t
155	IMP-54	20	IMP9 Street	t
156	IMP-55	14	IMP3 Street	t
157	IMP-56	20	IMP9 Street	t
158	IMP-57	14	IMP3 Street	t
159	IMP-58	23	IMP12 Street	t
160	IMP-59	14	IMP3 Street	t
161	IMP-60	25	IMP14 Street	t
162	IMP-61	12	IMP1 Street	t
163	IMP-62	22	IMP11 Street	t
164	IMP-63	16	IMP5 Street	t
165	IMP-64	11	IMP0 Street	t
166	IMP-65	16	IMP5 Street	t
167	IMP-66	20	IMP9 Street	t
168	IMP-67	11	IMP0 Street	t
169	IMP-68	20	IMP9 Street	t
170	IMP-69	16	IMP5 Street	t
171	IMP-70	14	IMP3 Street	t
172	IMP-71	14	IMP3 Street	t
173	IMP-72	12	IMP1 Street	t
174	IMP-73	22	IMP11 Street	t
175	IMP-74	19	IMP8 Street	t
176	IMP-75	12	IMP1 Street	t
177	IMP-76	19	IMP8 Street	t
178	IMP-77	21	IMP10 Street	t
179	IMP-78	20	IMP9 Street	t
180	IMP-79	23	IMP12 Street	t
181	IMP-80	14	IMP3 Street	t
182	IMP-81	23	IMP12 Street	t
183	IMP-82	18	IMP7 Street	t
184	IMP-83	20	IMP9 Street	t
185	IMP-84	15	IMP4 Street	t
186	IMP-85	12	IMP1 Street	t
187	IMP-86	20	IMP9 Street	t
188	IMP-87	21	IMP10 Street	t
189	IMP-88	12	IMP1 Street	t
190	IMP-89	12	IMP1 Street	t
191	IMP-90	17	IMP6 Street	t
192	IMP-91	24	IMP13 Street	t
193	IMP-92	25	IMP14 Street	t
194	IMP-93	12	IMP1 Street	t
195	IMP-94	22	IMP11 Street	t
196	IMP-95	13	IMP2 Street	t
197	IMP-96	13	IMP2 Street	t
198	IMP-97	12	IMP1 Street	t
199	IMP-98	22	IMP11 Street	t
200	IMP-99	20	IMP9 Street	t
\.


--
-- TOC entry 5081 (class 0 OID 16406)
-- Dependencies: 222
-- Data for Name: parking_zones; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.parking_zones (zone_id, zone_code, zone_name, hourly_rate, max_parking_minutes) FROM stdin;
1	A1	Central Zone	8.00	120
2	A2	Mall Zone	10.00	180
3	B1	Residential North	5.50	240
4	B2	Residential South	6.00	240
5	C1	Hospital Zone	7.50	90
6	C2	University Zone	4.50	300
7	D1	Beach Zone	12.00	180
8	D2	Business District	11.00	120
9	E1	Old City	9.00	150
10	E2	Train Station	6.50	60
11	IMP0	Magnolia Way	1.77	120
12	IMP1	Summit Ln	70.23	120
13	IMP2	Fifth Dr	56.33	120
14	IMP3	Downing Ave	24.36	120
15	IMP4	Elm Ct	43.27	120
16	IMP5	Central Way	35.37	120
17	IMP6	Queen St	87.99	120
18	IMP7	Main St	56.22	120
19	IMP8	Lansdowne Blvd	17.29	120
20	IMP9	Adams Ave	55.27	120
21	IMP10	State Blvd	70.82	120
22	IMP11	Newbury Way	0.80	120
23	IMP12	Jefferson Ave	60.43	120
24	IMP13	Liberty Ln	67.40	120
25	IMP14	Franklin Ave	88.28	120
\.


--
-- TOC entry 5086 (class 0 OID 16581)
-- Dependencies: 227
-- Data for Name: payment_transactions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.payment_transactions (transaction_id, event_id, vehicle_id, space_id, zone_code, start_time, stop_time, amount, created_at) FROM stdin;
182	182	1	1	A1	2026-04-20 21:06:01.618169	2026-04-20 22:48:35.877194	16.00	2026-04-20 22:48:35.832851
184	184	1	1	A1	2026-04-20 22:48:35.832851	2026-04-20 22:48:37.072272	8.00	2026-04-20 22:48:37.066206
\.


--
-- TOC entry 5079 (class 0 OID 16394)
-- Dependencies: 220
-- Data for Name: vehicles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.vehicles (vehicle_id, plate_number, owner_name) FROM stdin;
1	604-95-839	Nora Levin
2	089-64-318	Omer Cohen
3	058-28-878	Maya Azulay
4	394-23-797	Daniel Shalev
5	487-36-686	Lior Green
6	682-14-762	Roni Katz
7	513-77-315	Yael Mor
\.


--
-- TOC entry 5097 (class 0 OID 0)
-- Dependencies: 225
-- Name: parking_events_event_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.parking_events_event_id_seq', 184, true);


--
-- TOC entry 5098 (class 0 OID 0)
-- Dependencies: 223
-- Name: parking_spaces_space_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.parking_spaces_space_id_seq', 203, true);


--
-- TOC entry 5099 (class 0 OID 0)
-- Dependencies: 221
-- Name: parking_zones_zone_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.parking_zones_zone_id_seq', 28, true);


--
-- TOC entry 5100 (class 0 OID 0)
-- Dependencies: 219
-- Name: vehicles_vehicle_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.vehicles_vehicle_id_seq', 7, true);


--
-- TOC entry 4920 (class 2606 OID 16627)
-- Name: citations citations_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citations
    ADD CONSTRAINT citations_pkey PRIMARY KEY (citation_id);


--
-- TOC entry 4914 (class 2606 OID 16459)
-- Name: parking_events parking_events_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_events
    ADD CONSTRAINT parking_events_pkey PRIMARY KEY (event_id);


--
-- TOC entry 4907 (class 2606 OID 16432)
-- Name: parking_spaces parking_spaces_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_spaces
    ADD CONSTRAINT parking_spaces_pkey PRIMARY KEY (space_id);


--
-- TOC entry 4909 (class 2606 OID 16434)
-- Name: parking_spaces parking_spaces_space_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_spaces
    ADD CONSTRAINT parking_spaces_space_number_key UNIQUE (space_number);


--
-- TOC entry 4902 (class 2606 OID 16418)
-- Name: parking_zones parking_zones_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_zones
    ADD CONSTRAINT parking_zones_pkey PRIMARY KEY (zone_id);


--
-- TOC entry 4904 (class 2606 OID 16420)
-- Name: parking_zones parking_zones_zone_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_zones
    ADD CONSTRAINT parking_zones_zone_code_key UNIQUE (zone_code);


--
-- TOC entry 4918 (class 2606 OID 16596)
-- Name: payment_transactions payment_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_pkey PRIMARY KEY (transaction_id);


--
-- TOC entry 4898 (class 2606 OID 16402)
-- Name: vehicles vehicles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.vehicles
    ADD CONSTRAINT vehicles_pkey PRIMARY KEY (vehicle_id);


--
-- TOC entry 4900 (class 2606 OID 16404)
-- Name: vehicles vehicles_plate_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.vehicles
    ADD CONSTRAINT vehicles_plate_number_key UNIQUE (plate_number);


--
-- TOC entry 4921 (class 1259 OID 16639)
-- Name: idx_citations_inspection_time; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_citations_inspection_time ON public.citations USING btree (inspection_time DESC);


--
-- TOC entry 4922 (class 1259 OID 16638)
-- Name: idx_citations_vehicle_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_citations_vehicle_id ON public.citations USING btree (vehicle_id);


--
-- TOC entry 4910 (class 1259 OID 16473)
-- Name: idx_events_space_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_events_space_id ON public.parking_events USING btree (space_id);


--
-- TOC entry 4911 (class 1259 OID 16474)
-- Name: idx_events_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_events_status ON public.parking_events USING btree (status);


--
-- TOC entry 4912 (class 1259 OID 16472)
-- Name: idx_events_vehicle_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_events_vehicle_id ON public.parking_events USING btree (vehicle_id);


--
-- TOC entry 4915 (class 1259 OID 16613)
-- Name: idx_payment_transactions_stop_time; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_transactions_stop_time ON public.payment_transactions USING btree (stop_time DESC);


--
-- TOC entry 4916 (class 1259 OID 16612)
-- Name: idx_payment_transactions_vehicle_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_transactions_vehicle_id ON public.payment_transactions USING btree (vehicle_id);


--
-- TOC entry 4905 (class 1259 OID 16471)
-- Name: idx_spaces_space_number; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_spaces_space_number ON public.parking_spaces USING btree (space_number);


--
-- TOC entry 4896 (class 1259 OID 16470)
-- Name: idx_vehicles_plate_number; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_vehicles_plate_number ON public.vehicles USING btree (plate_number);


--
-- TOC entry 4929 (class 2606 OID 16633)
-- Name: citations citations_space_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citations
    ADD CONSTRAINT citations_space_id_fkey FOREIGN KEY (space_id) REFERENCES public.parking_spaces(space_id) ON DELETE RESTRICT;


--
-- TOC entry 4930 (class 2606 OID 16628)
-- Name: citations citations_vehicle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.citations
    ADD CONSTRAINT citations_vehicle_id_fkey FOREIGN KEY (vehicle_id) REFERENCES public.vehicles(vehicle_id) ON DELETE RESTRICT;


--
-- TOC entry 4924 (class 2606 OID 16465)
-- Name: parking_events fk_event_space; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_events
    ADD CONSTRAINT fk_event_space FOREIGN KEY (space_id) REFERENCES public.parking_spaces(space_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 4925 (class 2606 OID 16460)
-- Name: parking_events fk_event_vehicle; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_events
    ADD CONSTRAINT fk_event_vehicle FOREIGN KEY (vehicle_id) REFERENCES public.vehicles(vehicle_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 4923 (class 2606 OID 16435)
-- Name: parking_spaces fk_space_zone; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parking_spaces
    ADD CONSTRAINT fk_space_zone FOREIGN KEY (zone_id) REFERENCES public.parking_zones(zone_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 4926 (class 2606 OID 16597)
-- Name: payment_transactions payment_transactions_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.parking_events(event_id) ON DELETE RESTRICT;


--
-- TOC entry 4927 (class 2606 OID 16607)
-- Name: payment_transactions payment_transactions_space_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_space_id_fkey FOREIGN KEY (space_id) REFERENCES public.parking_spaces(space_id) ON DELETE RESTRICT;


--
-- TOC entry 4928 (class 2606 OID 16602)
-- Name: payment_transactions payment_transactions_vehicle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_vehicle_id_fkey FOREIGN KEY (vehicle_id) REFERENCES public.vehicles(vehicle_id) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS public.customers (
    customer_id character varying(32) PRIMARY KEY,
    vehicle_id integer UNIQUE REFERENCES public.vehicles(vehicle_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    display_name character varying(100) NOT NULL,
    password_hash character varying(128) NOT NULL,
    password_salt character varying(64) NOT NULL,
    password_iterations integer NOT NULL CHECK (password_iterations > 0),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE public.customers OWNER TO postgres;

ALTER TABLE public.customers ADD COLUMN IF NOT EXISTS vehicle_id integer UNIQUE REFERENCES public.vehicles(vehicle_id) ON UPDATE CASCADE ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_customers_vehicle_id ON public.customers (vehicle_id);

INSERT INTO public.customers (customer_id, vehicle_id, display_name, password_hash, password_salt, password_iterations, is_active)
VALUES
    ('CUST-1001', (SELECT vehicle_id FROM public.vehicles WHERE plate_number = '604-95-839'), 'Customer One', 'fe0b1603910880a265f613cba12d1386a38c128573d8b8e05a44d2176ff2a4f4', 'c1ebf599ba489f3869442f2087a95d98', 120000, true),
    ('CUST-1002', (SELECT vehicle_id FROM public.vehicles WHERE plate_number = '089-64-318'), 'Customer Two', '5291c8a3d5efc839c9a1e9c2c462ee2ddb9e99de8c24cac544cecaedc7d328ea', '1ccb4fb19c1887b57a71f93b501c53ae', 120000, true),
    ('CUST-1003', (SELECT vehicle_id FROM public.vehicles WHERE plate_number = '058-28-878'), 'Customer Three', '3d54531654d8fefddcd7d3371680980985cbbbcff5b4c9ac306fcec22fbeff0c', '4cba9cbe0f32e7351b823bb196df0c57', 120000, true),
    ('CUST-1004', (SELECT vehicle_id FROM public.vehicles WHERE plate_number = '394-23-797'), 'Customer Four', '3d6c8a14089d21b3a785067d230c484d0683cc298e8c4e43b3edddfcd44391e4', '051d18dd51a7df5247cc1935ff991a11', 120000, true)
ON CONFLICT (customer_id) DO UPDATE SET
    vehicle_id = EXCLUDED.vehicle_id;

CREATE TABLE IF NOT EXISTS public.mo_users (
    user_id character varying(32) PRIMARY KEY,
    display_name character varying(100) NOT NULL,
    password_hash character varying(128) NOT NULL,
    password_salt character varying(64) NOT NULL,
    password_iterations integer NOT NULL CHECK (password_iterations > 0),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE public.mo_users OWNER TO postgres;

INSERT INTO public.mo_users (user_id, display_name, password_hash, password_salt, password_iterations, is_active)
VALUES
    ('MO-1001', 'MO User One', 'f0e602a7cf1d8b9c2d23bef6e159f1580c77c66dd5fd69305d98bcbfbf9b9068', '50177e7c71848ab741a8a02d2e28740c', 120000, true),
    ('MO-1002', 'MO User Two', '4b1873875d2ee91b7e7d32b1ba688c8156ce2fce0929f7a102a1e3e0aff3b788', '2b6014db29a2b9e4b0cd9b2cf033f528', 120000, true),
    ('MO-1003', 'MO User Three', 'fce0a09d883967b58eac790687a314db8dcdbac64235f175174752f346b834c7', '9f08d807a00024db4adb5668a1e40f74', 120000, true),
    ('MO-1004', 'MO User Four', 'cf22b07065aa5dd5a3ff18447f9c6dd99f6bd0399255d0f6087f30d93915457c', '55a7f801c8fa9e462f9d9a5c0130ed5e', 120000, true)
ON CONFLICT (user_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    password_hash = EXCLUDED.password_hash,
    password_salt = EXCLUDED.password_salt,
    password_iterations = EXCLUDED.password_iterations,
    is_active = EXCLUDED.is_active;

CREATE TABLE IF NOT EXISTS public.peo_users (
    user_id character varying(32) PRIMARY KEY,
    display_name character varying(100) NOT NULL,
    password_hash character varying(128) NOT NULL,
    password_salt character varying(64) NOT NULL,
    password_iterations integer NOT NULL CHECK (password_iterations > 0),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE public.peo_users OWNER TO postgres;

INSERT INTO public.peo_users (user_id, display_name, password_hash, password_salt, password_iterations, is_active)
VALUES
    ('PEO-1001', 'PEO User One', 'a5c63b0d0e96249657c9179f383f05c0eaad916badaa64aab8955cf8149286d3', 'e7ae1ab718f992f797238687e91469f6', 120000, true),
    ('PEO-1002', 'PEO User Two', '5a41fcaadcb56a83ae938d881e3c3dad25820c2877466fc405c90b069d859544', 'b88c3749f0d0137b39b7fee6acb564a8', 120000, true),
    ('PEO-1003', 'PEO User Three', 'a576bb78f5adf21ff1a95aca43f1e7ef8c8793732a5f0f636efb1589adbc8c04', 'b726993f33dfe82a096886b4545fe8d7', 120000, true),
    ('PEO-1004', 'PEO User Four', '645830426e1f28ecb409e4f4f91346c2fe2c056290421c5fbce7e4fb87830619', '1728a450d8474055bfdc7e7c2367221c', 120000, true)
ON CONFLICT (user_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    password_hash = EXCLUDED.password_hash,
    password_salt = EXCLUDED.password_salt,
    password_iterations = EXCLUDED.password_iterations,
    is_active = EXCLUDED.is_active;


-- Completed on 2026-04-21 00:19:52

--
-- PostgreSQL database dump complete
--
