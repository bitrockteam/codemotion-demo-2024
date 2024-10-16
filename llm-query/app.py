import os
import ast
import re
import json
from logging.config import dictConfig
from flask import Flask, request, Response
from langchain_openai import ChatOpenAI
from langchain_community.utilities import SQLDatabase
from dotenv import load_dotenv
from langchain_community.agent_toolkits import SQLDatabaseToolkit
from langchain_core.messages import SystemMessage
from langchain_core.messages import HumanMessage
from langgraph.prebuilt import create_react_agent
from langchain.agents.agent_toolkits import create_retriever_tool
from langchain_community.vectorstores import FAISS
from langchain_openai import OpenAIEmbeddings
from radicalbit_client.rag import RadicalbitRagClient

load_dotenv()

db_host = os.environ.get("DATABASE_HOST")
db_port = os.environ.get("DATABASE_PORT")
db_name = os.environ.get("DATABASE_NAME")
db_user = os.environ.get("DATABASE_USER")
db_password = os.environ.get("DATABASE_PASSWORD")
api_key = os.environ.get('OPENAI_API_KEY')
sql_prefix = os.environ.get('SQL_PREFIX')
rb_platform_host = os.environ.get('RB_PLATFORM_HOST')
rb_platform_port = os.environ.get('RB_PLATFORM_PORT')
rb_platform_client_id = os.environ.get('RB_PLATFORM_CLIENT_ID')
rb_platform_client_secret = os.environ.get('RB_PLATFORM_CLIENT_SECRET')
rb_platform_tenant = os.environ.get('RB_PLATFORM_TENANT')

dictConfig({
    'version': 1,
    'formatters': {'default': {
        'format': '[%(asctime)s] %(levelname)s in %(module)s: %(message)s',
    }},
    'handlers': {'wsgi': {
        'class': 'logging.StreamHandler',
        'stream': 'ext://flask.logging.wsgi_errors_stream',
        'formatter': 'default'
    }},
    'root': {
        'level': 'INFO',
        'handlers': ['wsgi']
    }
})

app = Flask(__name__)

app.logger.info('Initializing llm agent...')

db = SQLDatabase.from_uri(f"postgresql+psycopg2://{db_user}:{db_password}@{db_host}:{db_port}/{db_name}")
llm = ChatOpenAI(model="gpt-4o", api_key=api_key)
toolkit = SQLDatabaseToolkit(db=db, llm=llm)
tools = toolkit.get_tools()

def query_as_list(db, query):
    res = db.run(query)
    if len(res) > 0:
        res = [el for sub in ast.literal_eval(res) for el in sub if el]
        res = [re.sub(r"\b\d+\b", "", string).strip() for string in res]
        return list(set(res))
    else:
        return list()

names = query_as_list(db, "SELECT name FROM fleet_model")
manufacturers = query_as_list(db, "SELECT manufacturer FROM fleet_model")
colors = query_as_list(db, "SELECT color FROM vehicle")
zone = query_as_list(db, "SELECT zone FROM vehicle_position")
city = query_as_list(db, "SELECT city FROM vehicle_position")

vector_db = FAISS.from_texts(names + manufacturers + colors + zone + city, OpenAIEmbeddings())
retriever = vector_db.as_retriever(search_kwargs={"k": 5})
description = """Use to look up values to filter on. Input is an approximate spelling of the proper noun, output is \
valid proper nouns. Use the noun most similar to the search."""
retriever_tool = create_retriever_tool(
    retriever,
    name="search_proper_nouns",
    description=description,
)

system_message = SystemMessage(content=sql_prefix.format(table_names=db.get_usable_table_names()))
tools.append(retriever_tool)
agent = create_react_agent(llm, tools, state_modifier=system_message)

rb_rag_client = RadicalbitRagClient(
    host=rb_platform_host,
    port =int(rb_platform_port),
    client_id=rb_platform_client_id,
    client_secret=rb_platform_client_secret,
    tenant_name=rb_platform_tenant
)

app.logger.info('llm agent initialized')

def beautify_gpt_response(response):
    return response[list(response.keys())[0]]['messages'][0].pretty_repr()

@app.route("/health-check")
def health_check():
    app.logger.info("Feeling good!")
    return "OK"

@app.route("/query/sql", methods=['POST'])
def query_sql():
    query_string = request.json.get('query')
    app.logger.info(f'Received query: {query_string}')
    if query_string:
        result = agent.stream(
            {"messages": [HumanMessage(content=query_string)]}
        )
        list_result = list(result)
        try:
            for s in list_result:
                app.logger.info(beautify_gpt_response(s))
            sql_query = list_result[-3]['agent']['messages'][0].tool_calls[0]['args']['query']
        except:
            app.logger.error('Failed to extract query')
            sql_query = "n/d"
        response = list_result[-1]['agent']['messages'][0].content
        return Response(f'{{"text": {json.dumps(response)}, "query": {json.dumps(sql_query)}}}', mimetype='application/json')
    else:
        return "Missing query string", 400

@app.route("/query/document", methods=['POST'])
def query_doc():
    query_string = request.json.get('query')
    app.logger.info(f'Received query: {query_string}')
    if query_string:
        result = rb_rag_client.invoke(prompt=query_string)
        app.logger.info(result)
        response = result['answer']
        return Response(f'{{"text": {json.dumps(response)}}}', mimetype='application/json')
    else:
        return "Missing query string", 400

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)
