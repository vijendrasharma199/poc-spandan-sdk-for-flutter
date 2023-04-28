import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        primarySwatch: Colors.green,
      ),
      home: const MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const platform = MethodChannel('com.example.poc_spandan_sdk/sericom');
  static const _stream = EventChannel('com.example.poc_spandan_sdk/sericom_event');

  TextEditingController commandController = TextEditingController();

  //variables
  String _deviceInfoStatus = '';
  int counter = 0;
  final StringBuffer _receivedData = StringBuffer('');

  //setUp Device Connection
  Future<void> _setupDeviceConnection() async {
    String deviceInfoStatus = "";

    try {
      final bool deviceConnectionStatus = await platform.invokeMethod('setUpConnection');
      deviceInfoStatus = "Device Connection --> $deviceConnectionStatus";
    } on PlatformException catch (e) {
      deviceInfoStatus = "Failed to get device data: '${e.message}'.";
    }

    //update view
    setState(() {
      _deviceInfoStatus = deviceInfoStatus;
    });
  }

  //send command
  Future<void> _sendCommand() async {
    String commandResponse = "";

    try {
      //get send command response
      final bool startCommand = await platform
          .invokeMethod("sendCommand", {"command": commandController.text});
      commandResponse = startCommand.toString();

      _stream.receiveBroadcastStream().listen((data) {
        updateData(data, true);
      });

      /* Future.delayed(const Duration(seconds: 10), () {
        final bool stopCommand = platform.invokeMethod("sendCommand", {"command": "0"}) as bool;
        commandResponse = stopCommand.toString();
      });*/
    } on PlatformException catch (e) {
      commandResponse = "Failed to get device data: '${e.message}'.";
    }

    updateData(commandResponse, false);
  }

  void updateData(String data, bool isDataWithCounter) {
    setState(() {
      if (isDataWithCounter) {
        ++counter;
        String countValue = counter.toString();
        _receivedData.writeln("$data : Counter--> $countValue");
      } else {
        _receivedData.writeln(data);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              margin: const EdgeInsets.all(10),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Container(
                    width: MediaQuery.of(context).size.width,
                    height: MediaQuery.of(context).size.height / 10,
                    decoration:
                        BoxDecoration(border: Border.all(color: Colors.black)),
                    child: Text(
                      _deviceInfoStatus,
                      maxLines: 2,
                      textAlign: TextAlign.start,
                      style:
                          const TextStyle(fontSize: 14, color: Colors.black87),
                    ),
                  ),
                  ElevatedButton(
                    onPressed: _setupDeviceConnection,
                    child: const Text('Setup Connection'),
                  ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.all(10),
              child: TextField(
                controller: commandController,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  labelText: 'Enter command...',
                ),
              ),
            ),
            Container(
              margin: const EdgeInsets.all(10),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ElevatedButton(
                    onPressed: _sendCommand,
                    child: const Text('Send Command'),
                  ),
                ],
              ),
            ),
            Container(
              width: MediaQuery.of(context).size.width,
              height: 100,
              margin: const EdgeInsets.all(10),
              decoration:
                  BoxDecoration(border: Border.all(color: Colors.black)),
              child: Expanded(
                flex: 1,
                child: SingleChildScrollView(
                  scrollDirection: Axis.vertical, //.horizontal
                  reverse: true,
                  child: Text(
                    _receivedData.toString(),
                    textAlign: TextAlign.start,
                    style: const TextStyle(
                      fontSize: 12.0,
                      color: Colors.black87,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
