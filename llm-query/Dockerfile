FROM python:3.10
WORKDIR /app
COPY . /app
RUN pip install -r requirements.txt --extra-index-url https://europe-west3-python.pkg.dev/bionic-unity-294411/radicalbit-python-api/simple
ENTRYPOINT ["python", "app.py"]
